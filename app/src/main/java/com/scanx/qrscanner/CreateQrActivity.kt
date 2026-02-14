package com.scanx.qrscanner

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.lifecycle.lifecycleScope
import com.scanx.qrscanner.databinding.ActivityCreateQrBinding
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.*

class CreateQrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQrBinding
    private var currentBitmap: android.graphics.Bitmap? = null
    private var currentType: String = "TEXT"
    private var currentStep: Int = 0
    private var selectedFormat: com.google.zxing.BarcodeFormat = com.google.zxing.BarcodeFormat.QR_CODE
    
    // QR Generation Job for debouncing
    private var qrGenerationJob: kotlinx.coroutines.Job? = null
    private var selectedForegroundColor = Color.BLACK
    private var selectedSecondaryColor = Color.parseColor("#1A73E8")
    private var isRounded = false
    private var isGradient = false
    private var isSleek = false
    private var selectedLogoItem: LogoItem = LogoItem.None
    private var selectedStyle: QrStyle? = null

    private val availableColors = listOf(
        Color.BLACK,
        Color.parseColor("#1A73E8"), Color.parseColor("#D93025"),
        Color.parseColor("#188038"), Color.parseColor("#F29900"),
        Color.parseColor("#9334E6"), Color.parseColor("#007B83"),
        Color.parseColor("#C5221F"), Color.parseColor("#1967D2")
    )

    private val styleTemplates = listOf(
        QrStyle(R.string.style_classic, Color.BLACK, Color.BLACK, false, false, false),
        QrStyle(R.string.style_modern, Color.BLACK, Color.BLACK, false, true, true),
        QrStyle(R.string.style_neon, Color.parseColor("#1A73E8"), Color.parseColor("#9334E6"), true, true, true, 
            intArrayOf(Color.parseColor("#1A73E8"), Color.parseColor("#9334E6"))),
        QrStyle(R.string.style_ocean, Color.parseColor("#007B83"), Color.parseColor("#188038"), true, true, false, 
            intArrayOf(Color.parseColor("#007B83"), Color.parseColor("#188038"))),
        QrStyle(R.string.style_gold, Color.parseColor("#202124"), Color.parseColor("#F29900"), true, false, true, 
            intArrayOf(Color.parseColor("#202124"), Color.parseColor("#F29900"))),
        QrStyle(R.string.style_rose, Color.parseColor("#C5221F"), Color.parseColor("#9334E6"), true, true, false, 
            intArrayOf(Color.parseColor("#C5221F"), Color.parseColor("#9334E6")))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()

        savedInstanceState?.let {
            currentStep = it.getInt("currentStep", 0)
            currentType = it.getString("currentType", "TEXT")
            selectedForegroundColor = it.getInt("selectedForegroundColor", Color.BLACK)
            selectedSecondaryColor = it.getInt("selectedSecondaryColor", Color.parseColor("#1A73E8"))
            isRounded = it.getBoolean("isRounded", false)
            isGradient = it.getBoolean("isGradient", false)
            isSleek = it.getBoolean("isSleek", false)
            val logoType = it.getString("logoType", "NONE")
            selectedLogoItem = when (logoType) {
                "RESOURCE" -> LogoItem.Resource(it.getInt("logoResId"))
                else -> LogoItem.None
            }
            selectedFormat = BarcodeFormat.valueOf(it.getString("selectedFormat", "QR_CODE"))

            val styleRes = it.getInt("selectedStyleNameResId", -1)
            if (styleRes != -1) {
                selectedStyle = styleTemplates.find { s -> s.nameResId == styleRes }
            }

            // Restore navigation state
            updateStepUI(animate = false)
        } ?: run {
            updateStepUI(animate = false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentStep", currentStep)
        outState.putString("currentType", currentType)
        outState.putInt("selectedForegroundColor", selectedForegroundColor)
        outState.putInt("selectedSecondaryColor", selectedSecondaryColor)
        outState.putBoolean("isRounded", isRounded)
        outState.putBoolean("isGradient", isGradient)
        outState.putBoolean("isSleek", isSleek)
        when (val item = selectedLogoItem) {
            is LogoItem.None -> outState.putString("logoType", "NONE")
            is LogoItem.Resource -> {
                outState.putString("logoType", "RESOURCE")
                outState.putInt("logoResId", item.resId)
            }
        }
        outState.putString("selectedFormat", selectedFormat.name)
        selectedStyle?.let { outState.putInt("selectedStyleNameResId", it.nameResId) }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { 
            handleBackNavigation()
        }

        // Selection Items
        binding.itemText.setOnClickListener { startInput("TEXT") }
        binding.itemEmail.setOnClickListener { startInput("EMAIL") }
        binding.itemUrl.setOnClickListener { startInput("URL") }
        binding.itemPhone.setOnClickListener { startInput("PHONE") }
        binding.itemSms.setOnClickListener { startInput("SMS") }
        binding.itemLocation.setOnClickListener { startInput("LOCATION") }
        binding.itemWifi.setOnClickListener { startInput("WIFI") }
        binding.itemVCard.setOnClickListener { startInput("VCARD") }
        binding.itemMeCard.setOnClickListener { startInput("MECARD") }
        binding.itemBizCard.setOnClickListener { startInput("BIZCARD") }
        binding.itemMarket.setOnClickListener { startInput("MARKET") }
        binding.itemEvent.setOnClickListener { startInput("EVENT") }

        binding.btnNextToStyle.setOnClickListener { 
            if (validateInput()) {
                currentStep = 2
                updateStepUI()
            }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                generateQR()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etInput.addTextChangedListener(textWatcher)
        binding.etWifiSsid.addTextChangedListener(textWatcher)
        binding.etWifiPassword.addTextChangedListener(textWatcher)
        binding.etContactName.addTextChangedListener(textWatcher)
        binding.etContactPhone.addTextChangedListener(textWatcher)
        binding.etContactEmail.addTextChangedListener(textWatcher)
        binding.etContactOrg.addTextChangedListener(textWatcher)
        binding.etEmailAddress.addTextChangedListener(textWatcher)
        binding.etEmailSubject.addTextChangedListener(textWatcher)
        binding.etEmailMessage.addTextChangedListener(textWatcher)
        binding.etSmsPhone.addTextChangedListener(textWatcher)
        binding.etSmsMessage.addTextChangedListener(textWatcher)
        binding.etLatitude.addTextChangedListener(textWatcher)
        binding.etLongitude.addTextChangedListener(textWatcher)
        binding.etEventTitle.addTextChangedListener(textWatcher)
        binding.etEventLocation.addTextChangedListener(textWatcher)
        binding.etEventDescription.addTextChangedListener(textWatcher)
        
        // WiFi Encryption Dropdown
        val encTypes = arrayOf(getString(R.string.wifi_enc_wpa), getString(R.string.wifi_enc_wpa3), getString(R.string.wifi_enc_wep), getString(R.string.wifi_enc_none))
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, encTypes)
        binding.actvWifiEncryption.setAdapter(adapter)
        binding.actvWifiEncryption.setText(encTypes[0], false) // Default to WPA
        binding.actvWifiEncryption.setOnItemClickListener { _, _, _, _ -> generateQR() }

        binding.btnShare.setOnClickListener { shareQR() }
        binding.btnSave.setOnClickListener { saveQRToGallery() }

        setupCustomizationUI()
    }

    private fun handleBackNavigation() {
        if (currentStep > 0) {
            currentStep--
            updateStepUI()
        } else {
            finish()
        }
    }

    private fun updateStepUI(animate: Boolean = true) {
        if (!animate) {
            binding.stepFlipper.inAnimation = null
            binding.stepFlipper.outAnimation = null
        } else {
            binding.stepFlipper.setInAnimation(this, android.R.anim.fade_in)
            binding.stepFlipper.setOutAnimation(this, android.R.anim.fade_out)
        }

        binding.stepFlipper.displayedChild = currentStep
        
        when (currentStep) {
            0 -> {
                binding.toolbar.title = getString(R.string.create_qr)
                binding.stepProgress.setProgress(33, true)
            }
            1 -> {
                binding.toolbar.title = getString(R.string.step_data)
                binding.stepProgress.setProgress(66, true)
            }
            2 -> {
                binding.toolbar.title = getString(R.string.step_style)
                binding.stepProgress.setProgress(100, true)
                generateQR()
            }
        }
    }

    private fun setupCustomizationUI() {
        // Smart Styles
        val styleAdapter = QrStyleAdapter(styleTemplates, selectedStyle) { style ->
            applyStyle(style)
        }
        binding.rvStyles.adapter = styleAdapter

        // Foreground Color Picker
        val colorAdapter = ColorPickerAdapter(availableColors, selectedForegroundColor) { color ->
            selectedForegroundColor = color
            generateQR()
        }
        binding.rvColors.adapter = colorAdapter

        // Secondary Color Picker
        val secondaryColorAdapter = ColorPickerAdapter(availableColors, selectedSecondaryColor) { color ->
            selectedSecondaryColor = color
            generateQR()
        }
        binding.rvSecondaryColors.adapter = secondaryColorAdapter

        // Gradient Toggle
        binding.swGradient.setOnCheckedChangeListener { _, isChecked ->
            isGradient = isChecked
            binding.llSecondaryColor.visibility = if (isChecked) View.VISIBLE else View.GONE
            generateQR()
        }

        // Rounded Toggle
        binding.swRounded.setOnCheckedChangeListener { _, isChecked ->
            isRounded = isChecked
            generateQR()
        }

        // Sleek Corners Toggle
        binding.swSleek.setOnCheckedChangeListener { _, isChecked ->
            isSleek = isChecked
            generateQR()
        }

        // Logo Picker
        val logoItems = listOf(
            LogoItem.None,
            LogoItem.Resource(R.drawable.ic_qr_code),
            LogoItem.Resource(R.drawable.ic_wifi),
            LogoItem.Resource(R.drawable.ic_person),
            LogoItem.Resource(R.drawable.ic_shopping_cart)
        )
        val logoAdapter = LogoPickerAdapter(logoItems, selectedLogoItem) { item ->
            selectedLogoItem = item
            generateQR()
        }
        binding.rvLogos.adapter = logoAdapter

        // Logo Toggle removed, replaced by branding section

        // Format Selection
        binding.cgFormat.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipQr -> {
                    selectedFormat = BarcodeFormat.QR_CODE
                    isRounded = false
                }
                R.id.chipQrDots -> {
                    selectedFormat = BarcodeFormat.QR_CODE
                    isRounded = true
                }
                R.id.chipAztec -> selectedFormat = BarcodeFormat.AZTEC
                R.id.chipDataMatrix -> selectedFormat = BarcodeFormat.DATA_MATRIX
                R.id.chipPdf417 -> selectedFormat = BarcodeFormat.PDF_417
                R.id.chipCode128 -> selectedFormat = BarcodeFormat.CODE_128
            }
            
            val isQR = selectedFormat == BarcodeFormat.QR_CODE
            val is2D = isQR || selectedFormat == BarcodeFormat.AZTEC || selectedFormat == BarcodeFormat.DATA_MATRIX || selectedFormat == BarcodeFormat.PDF_417
            
            binding.llShapeOptions.visibility = if (isQR) View.VISIBLE else View.GONE
            binding.cardBranding.visibility = if (isQR) View.VISIBLE else View.GONE
            binding.llAdvancedStyle.visibility = if (is2D) View.VISIBLE else View.GONE
            binding.swRounded.isChecked = isRounded
            
            if (!isQR) {
                selectedLogoItem = LogoItem.None
            }
            
            generateQR()
        }
    }

    private fun applyStyle(style: QrStyle) {
        selectedStyle = style
        selectedForegroundColor = style.foregroundColor
        selectedSecondaryColor = style.secondaryColor
        isGradient = style.isGradient
        isRounded = style.isRounded
        isSleek = style.isSleek
        
        // Update UI components
        binding.swGradient.isChecked = isGradient
        binding.swRounded.isChecked = isRounded
        binding.swSleek.isChecked = isSleek
        binding.llSecondaryColor.visibility = if (isGradient) View.VISIBLE else View.GONE
        
        refreshCustomizationAdapters()
        generateQR()
    }

    private fun refreshCustomizationAdapters() {
        setupCustomizationUI()
    }

    private fun showSelection() {
        binding.selectionContainer.visibility = View.VISIBLE
        binding.dataInputContainer.visibility = View.GONE
        binding.toolbar.title = getString(R.string.create_qr)
    }

    private fun startInput(type: String) {
        currentType = type
        currentStep = 1
        updateStepUI()
        
        binding.etWifiSsid.text?.clear()
        binding.etWifiPassword.text?.clear()
        binding.etContactName.text?.clear()
        binding.etContactPhone.text?.clear()
        binding.etContactEmail.text?.clear()
        binding.etContactOrg.text?.clear()
        binding.etEmailAddress.text?.clear()
        binding.etEmailSubject.text?.clear()
        binding.etEmailMessage.text?.clear()
        binding.etLatitude.text?.clear()
        binding.etLongitude.text?.clear()
        binding.etEventTitle.text?.clear()
        binding.etEventLocation.text?.clear()
        binding.etEventDescription.text?.clear()
        binding.etSmsPhone.text?.clear()
        binding.etSmsMessage.text?.clear()
        binding.actvWifiEncryption.setText(getString(R.string.wifi_enc_wpa), false)
        
        // Adjust visibility based on type
        binding.tilInput.visibility = View.GONE
        binding.llWifiInputs.visibility = View.GONE
        binding.llContactInputs.visibility = View.GONE
        binding.llEmailInputs.visibility = View.GONE
        binding.llSmsInputs.visibility = View.GONE
        binding.llLocationInputs.visibility = View.GONE
        binding.llEventInputs.visibility = View.GONE
        
        when (type) {
            "WIFI" -> {
                binding.llWifiInputs.visibility = View.VISIBLE
            }
            "VCARD", "MECARD", "BIZCARD" -> {
                binding.llContactInputs.visibility = View.VISIBLE
            }
            "EMAIL" -> {
                binding.llEmailInputs.visibility = View.VISIBLE
            }
            "SMS" -> {
                binding.llSmsInputs.visibility = View.VISIBLE
            }
            "LOCATION" -> {
                binding.llLocationInputs.visibility = View.VISIBLE
            }
            "EVENT" -> {
                binding.llEventInputs.visibility = View.VISIBLE
            }
            else -> {
                binding.tilInput.visibility = View.VISIBLE
                binding.tilInput.hint = when (type) {
                    "TEXT" -> getString(R.string.hint_text)
                    "URL" -> getString(R.string.hint_url)
                    "PHONE" -> getString(R.string.hint_phone_number)
                    "MARKET" -> getString(R.string.hint_market)
                    "EVENT" -> getString(R.string.calendar_event)
                    else -> getString(R.string.hint_text)
                }
            }
        }
        
        generateQR()
    }

    private fun validateInput(): Boolean {
        // Clear all errors first
        binding.tilInput.error = null
        binding.tilWifiSsid.error = null
        binding.tilWifiPassword.error = null
        binding.tilContactName.error = null
        binding.tilContactPhone.error = null
        binding.tilContactEmail.error = null
        binding.tilContactOrg.error = null
        binding.tilEmailAddress.error = null
        binding.tilEmailSubject.error = null
        binding.tilEmailMessage.error = null
        binding.tilSmsPhone.error = null
        binding.tilSmsMessage.error = null
        binding.tilLatitude.error = null
        binding.tilLongitude.error = null
        binding.tilContactTitle.error = null
        binding.tilContactUrl.error = null
        binding.tilContactAddress.error = null
        binding.tilContactNote.error = null
        binding.tilEventTitle.error = null
        binding.tilEventLocation.error = null
        binding.tilEventDescription.error = null

        var isValid = true

        when (currentType) {
            "TEXT" -> {
                if (binding.etInput.text.isNullOrEmpty()) {
                    isValid = false
                }
            }
            "URL" -> {
                val input = binding.etInput.text.toString()
                if (input.isEmpty()) {
                    isValid = false
                } else if (!Patterns.WEB_URL.matcher(input).matches()) {
                    binding.tilInput.error = getString(R.string.error_invalid_url)
                    isValid = false
                }
            }
            "EMAIL" -> {
                val input = binding.etEmailAddress.text.toString()
                if (input.isEmpty()) {
                    isValid = false
                } else if (!Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
                    binding.tilEmailAddress.error = getString(R.string.error_invalid_email)
                    isValid = false
                }
            }
            "PHONE" -> {
                val input = binding.etInput.text.toString()
                if (input.isEmpty()) {
                    isValid = false
                } else if (!Patterns.PHONE.matcher(input).matches()) {
                    binding.tilInput.error = getString(R.string.error_invalid_phone)
                    isValid = false
                }
            }
            "SMS" -> {
                val phone = binding.etSmsPhone.text.toString()
                if (phone.isEmpty()) {
                    binding.tilSmsPhone.error = getString(R.string.error_invalid_phone)
                    isValid = false
                }
            }
            "LOCATION" -> {
                val latStr = binding.etLatitude.text.toString()
                val lonStr = binding.etLongitude.text.toString()
                
                if (latStr.isEmpty()) {
                    binding.tilLatitude.error = getString(R.string.error_invalid_input)
                    isValid = false
                } else {
                    val lat = latStr.toDoubleOrNull()
                    if (lat == null || lat < -90.0 || lat > 90.0) {
                        binding.tilLatitude.error = getString(R.string.error_invalid_input)
                        isValid = false
                    }
                }
                
                if (lonStr.isEmpty()) {
                    binding.tilLongitude.error = getString(R.string.error_invalid_input)
                    isValid = false
                } else {
                    val lon = lonStr.toDoubleOrNull()
                    if (lon == null || lon < -180.0 || lon > 180.0) {
                        binding.tilLongitude.error = getString(R.string.error_invalid_input)
                        isValid = false
                    }
                }
            }
            "MARKET" -> {
                if (binding.etInput.text.isNullOrEmpty()) {
                    isValid = false
                }
            }
            "WIFI" -> {
                if (binding.etWifiSsid.text.isNullOrEmpty()) {
                    binding.tilWifiSsid.error = getString(R.string.error_ssid_required)
                    isValid = false
                }
            }
            "VCARD", "MECARD", "BIZCARD" -> {
                if (binding.etContactName.text.isNullOrEmpty()) {
                    binding.tilContactName.error = getString(R.string.error_name_required)
                    isValid = false
                }
            }
            "EVENT" -> {
                if (binding.etEventTitle.text.isNullOrEmpty()) {
                    binding.tilEventTitle.error = getString(R.string.error_title_required)
                    isValid = false
                }
            }
        }

        return isValid
    }

    private fun generateQR() {
        qrGenerationJob?.cancel()
        qrGenerationJob = lifecycleScope.launch {
            if (currentStep < 2) {
                // Debounce typing if not on the style page
                delay(300)
            }
            performGeneration()
        }
    }

    private suspend fun performGeneration() {
        val isValid = validateInput()
        updateSettingsLock(isValid)
        
        if (!isValid && currentStep < 2) {
            binding.ivQrPreview.setImageBitmap(null)
            currentBitmap = null
            return
        }

        val content = when (currentType) {
            "EMAIL" -> {
                val address = binding.etEmailAddress.text.toString()
                val subject = binding.etEmailSubject.text.toString()
                val message = binding.etEmailMessage.text.toString()
                if (address.isEmpty()) "" else {
                    val cleanAddress = if (address.lowercase().startsWith("mailto:")) address.substring(7) else address
                    var mailto = "mailto:$cleanAddress"
                    val params = mutableListOf<String>()
                    if (subject.isNotEmpty()) params.add("subject=${Uri.encode(subject)}")
                    if (message.isNotEmpty()) params.add("body=${Uri.encode(message)}")
                    if (params.isNotEmpty()) mailto += "?" + params.joinToString("&")
                    mailto
                }
            }
            "SMS" -> {
                val phone = binding.etSmsPhone.text.toString()
                val message = binding.etSmsMessage.text.toString()
                if (phone.isEmpty()) "" else {
                    val cleanPhone = when {
                        phone.lowercase().startsWith("smsto:") -> phone.substring(6)
                        phone.lowercase().startsWith("sms:") -> phone.substring(4)
                        else -> phone
                    }
                    if (message.isNotEmpty()) "sms:$cleanPhone?body=${Uri.encode(message)}" else "sms:$cleanPhone"
                }
            }
            "LOCATION" -> {
                val lat = binding.etLatitude.text.toString()
                val lon = binding.etLongitude.text.toString()
                if (lat.isEmpty() || lon.isEmpty()) "" else {
                    val cleanLat = if (lat.lowercase().startsWith("geo:")) lat.substring(4) else lat
                    "geo:$cleanLat,$lon"
                }
            }
            "WIFI" -> {
                val ssid = binding.etWifiSsid.text.toString()
                val pass = binding.etWifiPassword.text.toString()
                val encryption = binding.actvWifiEncryption.text.toString()
                val encType = when (encryption) {
                    getString(R.string.wifi_enc_wpa) -> "WPA"
                    getString(R.string.wifi_enc_wpa3) -> "SAE"
                    getString(R.string.wifi_enc_wep) -> "WEP"
                    else -> "nopass"
                }
                if (ssid.isEmpty()) "" else {
                    val escapedSsid = qrEscapeWifi(ssid)
                    val escapedPass = qrEscapeWifi(pass)
                    if (encType == "nopass") "WIFI:S:$escapedSsid;T:nopass;;"
                    else "WIFI:S:$escapedSsid;T:$encType;P:$escapedPass;;"
                }
            }
            "VCARD" -> {
                val name = binding.etContactName.text.toString()
                val phone = binding.etContactPhone.text.toString()
                val email = binding.etContactEmail.text.toString()
                val org = binding.etContactOrg.text.toString()
                val title = binding.etContactTitle.text.toString()
                val url = binding.etContactUrl.text.toString()
                val address = binding.etContactAddress.text.toString()
                val note = binding.etContactNote.text.toString()
                
                if (name.isEmpty()) "" else {
                    val sb = StringBuilder()
                    sb.append("BEGIN:VCARD\n")
                    sb.append("VERSION:3.0\n")
                    sb.append("N:").append(qrEscapeVCard(name)).append(";;;\n")
                    sb.append("FN:").append(qrEscapeVCard(name)).append("\n")
                    if (org.isNotEmpty()) sb.append("ORG:").append(qrEscapeVCard(org)).append("\n")
                    if (title.isNotEmpty()) sb.append("TITLE:").append(qrEscapeVCard(title)).append("\n")
                    if (phone.isNotEmpty()) sb.append("TEL:").append(qrEscapeVCard(phone)).append("\n")
                    if (email.isNotEmpty()) sb.append("EMAIL:").append(qrEscapeVCard(email)).append("\n")
                    if (url.isNotEmpty()) sb.append("URL:").append(qrEscapeVCard(url)).append("\n")
                    if (address.isNotEmpty()) sb.append("ADR:").append(qrEscapeVCard(address)).append("\n")
                    if (note.isNotEmpty()) sb.append("NOTE:").append(qrEscapeVCard(note)).append("\n")
                    sb.append("END:VCARD")
                    sb.toString()
                }
            }
            "MECARD" -> {
                val name = binding.etContactName.text.toString()
                val phone = binding.etContactPhone.text.toString()
                val email = binding.etContactEmail.text.toString()
                val org = binding.etContactOrg.text.toString()
                val address = binding.etContactAddress.text.toString()
                val url = binding.etContactUrl.text.toString()
                val note = binding.etContactNote.text.toString()

                if (name.isEmpty()) "" else {
                    val sb = StringBuilder("MECARD:")
                    sb.append("N:").append(qrEscapeMeCard(name)).append(";")
                    if (org.isNotEmpty()) sb.append("ORG:").append(qrEscapeMeCard(org)).append(";")
                    if (phone.isNotEmpty()) sb.append("TEL:").append(qrEscapeMeCard(phone)).append(";")
                    if (email.isNotEmpty()) sb.append("EMAIL:").append(qrEscapeMeCard(email)).append(";")
                    if (url.isNotEmpty()) sb.append("URL:").append(qrEscapeMeCard(url)).append(";")
                    if (address.isNotEmpty()) sb.append("ADR:").append(qrEscapeMeCard(address)).append(";")
                    if (note.isNotEmpty()) sb.append("NOTE:").append(qrEscapeMeCard(note)).append(";")
                    sb.append(";")
                    sb.toString()
                }
            }
            "BIZCARD" -> {
                val name = binding.etContactName.text.toString()
                val phone = binding.etContactPhone.text.toString()
                val email = binding.etContactEmail.text.toString()
                val org = binding.etContactOrg.text.toString()
                val title = binding.etContactTitle.text.toString()

                if (name.isEmpty()) "" else {
                    val sb = StringBuilder("BIZCARD:")
                    sb.append("N:").append(qrEscapeMeCard(name)).append(";")
                    if (title.isNotEmpty()) sb.append("T:").append(qrEscapeMeCard(title)).append(";")
                    if (org.isNotEmpty()) sb.append("C:").append(qrEscapeMeCard(org)).append(";")
                    if (phone.isNotEmpty()) sb.append("P:").append(qrEscapeMeCard(phone)).append(";")
                    if (email.isNotEmpty()) sb.append("E:").append(qrEscapeMeCard(email)).append(";")
                    sb.append(";")
                    sb.toString()
                }
            }
            "EVENT" -> {
                val title = binding.etEventTitle.text.toString()
                val loc = binding.etEventLocation.text.toString()
                val desc = binding.etEventDescription.text.toString()
                if (title.isEmpty()) "" else {
                    val sb = StringBuilder()
                    sb.append("BEGIN:VEVENT\n")
                    sb.append("SUMMARY:").append(qrEscapeVCard(title)).append("\n")
                    if (loc.isNotEmpty()) sb.append("LOCATION:").append(qrEscapeVCard(loc)).append("\n")
                    if (desc.isNotEmpty()) sb.append("DESCRIPTION:").append(qrEscapeVCard(desc)).append("\n")
                    sb.append("END:VEVENT")
                    sb.toString()
                }
            }
            "TEXT", "URL", "PHONE", "MARKET" -> {
                val input = binding.etInput.text.toString()
                if (input.isEmpty()) "" else when (currentType) {
                    "URL" -> if (input.lowercase().startsWith("http://") || input.lowercase().startsWith("https://")) input else "https://$input"
                    "PHONE" -> "tel:${if (input.lowercase().startsWith("tel:")) input.substring(4) else input}"
                    "MARKET" -> if (input.lowercase().startsWith("market://")) input else "market://details?id=$input"
                    else -> input
                }
            }
            else -> ""
        }

        if (content.isEmpty()) {
            binding.ivQrPreview.setImageBitmap(null)
            currentBitmap = null
            return
        }

        if (content.length > 2048) {
            Toast.makeText(this, R.string.error_content_too_long, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
                hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
                hints[EncodeHintType.MARGIN] = 1
                if (selectedLogoItem !is LogoItem.None) {
                    hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
                }

                val bitMatrix = com.google.zxing.MultiFormatWriter().encode(
                    content, 
                    selectedFormat, 
                    if (selectedFormat == BarcodeFormat.QR_CODE) 512 else 800, 
                    if (selectedFormat == BarcodeFormat.QR_CODE) 512 else 400, 
                    hints
                )
                
                val inputWidth = bitMatrix.width
                val inputHeight = bitMatrix.height
                
                // Target display size
                val outputWidth = inputWidth
                val outputHeight = inputHeight
                
                val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

                // Background
                canvas.drawColor(Color.WHITE)

                // Setup Foreground Paint
                if (isGradient) {
                    paint.shader = android.graphics.LinearGradient(
                        0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(),
                        selectedForegroundColor, selectedSecondaryColor,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                } else {
                    paint.color = selectedForegroundColor
                }
                
                if (selectedFormat == BarcodeFormat.QR_CODE || selectedFormat == BarcodeFormat.AZTEC || selectedFormat == BarcodeFormat.DATA_MATRIX) {
                    val moduleSizeX = outputWidth.toFloat() / inputWidth
                    val moduleSizeY = outputHeight.toFloat() / inputHeight
                    val finderPatternSize = 7
                    
                    for (y in 0 until inputHeight) {
                        for (x in 0 until inputWidth) {
                            if (bitMatrix[x, y]) {
                                val isFinderPattern = selectedFormat == BarcodeFormat.QR_CODE && (
                                    (x < finderPatternSize && y < finderPatternSize) ||
                                    (x >= inputWidth - finderPatternSize && y < finderPatternSize) ||
                                    (x < finderPatternSize && y >= inputHeight - finderPatternSize)
                                )
                                
                                val left = x * moduleSizeX
                                val top = y * moduleSizeY
                                val right = (x + 1) * moduleSizeX
                                val bottom = (y + 1) * moduleSizeY

                                if (isFinderPattern) {
                                    if (isSleek) continue
                                    if (isRounded) {
                                        val cornerRadius = moduleSizeX * 0.25f
                                        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
                                    } else {
                                        canvas.drawRect(left, top, right, bottom, paint)
                                    }
                                } else if (isRounded) {
                                    // Data modules as dots
                                    canvas.drawCircle(left + moduleSizeX / 2f, top + moduleSizeY / 2f, moduleSizeX * 0.35f, paint)
                                } else {
                                    canvas.drawRect(left, top, right, bottom, paint)
                                }
                            }
                        }
                    }

                    // Advanced Sleek Corners Overlay
                    if (selectedFormat == BarcodeFormat.QR_CODE && isSleek) {
                        val ms = moduleSizeX
                        drawSleekEyeball(canvas, 0f, 0f, ms, paint)
                        drawSleekEyeball(canvas, (inputWidth - 7) * ms, 0f, ms, paint)
                        drawSleekEyeball(canvas, 0f, (inputHeight - 7) * ms, ms, paint)
                    }

                    // Logo Overlay
                    if (selectedFormat == BarcodeFormat.QR_CODE && selectedLogoItem !is LogoItem.None) {
                        val logoSize = outputWidth / 5
                        val logoX = (outputWidth - logoSize) / 2f
                        val logoY = (outputHeight - logoSize) / 2f
                        
                        // White background for logo
                        val bgPaint = android.graphics.Paint().apply { 
                            color = Color.WHITE
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(logoX - 4, logoY - 4, logoX + logoSize + 4, logoY + logoSize + 4, 12f, 12f, bgPaint)
                        
                        // Draw selected logo
                        val logoBitmap = when (val item = selectedLogoItem) {
                            is LogoItem.Resource -> {
                                val d = androidx.core.content.ContextCompat.getDrawable(this@CreateQrActivity, item.resId)
                                d?.let {
                                    val b = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
                                    val c = android.graphics.Canvas(b)
                                    it.setBounds(0, 0, logoSize, logoSize)
                                    if (!isGradient) it.setTint(selectedForegroundColor)
                                    it.draw(c)
                                    b
                                }
                            }
                            else -> null
                        }

                        logoBitmap?.let {
                            canvas.drawBitmap(it, logoX, logoY, null)
                        }
                    }

                } else {
                    // 1D Barcode rendering - simple scaling
                    val scaleX = outputWidth.toFloat() / inputWidth
                    val scaleY = outputHeight.toFloat() / inputHeight
                    for (y in 0 until inputHeight) {
                        for (x in 0 until inputWidth) {
                            if (bitMatrix[x, y]) {
                                canvas.drawRect(x * scaleX, y * scaleY, (x + 1) * scaleX, (y + 1) * scaleY, paint)
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.ivQrPreview.setImageBitmap(bitmap)
                    currentBitmap = bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun drawSleekEyeball(canvas: android.graphics.Canvas, x: Float, y: Float, moduleSize: Float, paint: android.graphics.Paint) {
        val oldStyle = paint.style
        
        // Outer Frame
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = moduleSize
        val offset = moduleSize / 2f
        canvas.drawRoundRect(x + offset, y + offset, x + 7 * moduleSize - offset, y + 7 * moduleSize - offset, 2 * moduleSize, 2 * moduleSize, paint)
        
        // Inner Square
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(x + 2 * moduleSize, y + 2 * moduleSize, x + 5 * moduleSize, y + 5 * moduleSize, 1.5f * moduleSize, 1.5f * moduleSize, paint)
        
        paint.style = oldStyle
    }

    private fun shareQR() {
        val bitmap = currentBitmap ?: run {
            Toast.makeText(this, R.string.error_nothing_to_share, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cachePath = java.io.File(cacheDir, "images")
            cachePath.mkdirs()
            val stream = java.io.FileOutputStream("$cachePath/qr_code.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val imageFile = java.io.File(cachePath, "qr_code.png")
            val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_qr_data_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_sharing_qr), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveQRToGallery() {
        val bitmap = currentBitmap ?: run {
            Toast.makeText(this, R.string.error_nothing_to_share, Toast.LENGTH_SHORT).show()
            return
        }
        val filename = "QR_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver?.also { resolver ->
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = java.io.File(imagesDir, filename)
                fos = java.io.FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.qr_saved), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("CreateQrActivity", "Error saving QR", e)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.error_save), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSettingsLock(isValid: Boolean) {
        val actualAlpha = if (isValid) 1.0f else 0.5f
        
        binding.cardStyle.alpha = actualAlpha
        binding.cardBranding.alpha = actualAlpha
        
        // Disable interaction
        binding.cardStyle.isEnabled = isValid
        binding.cardBranding.isEnabled = isValid
        
        // Recursively disable children
        setViewsEnabled(binding.cardStyle, isValid)
        setViewsEnabled(binding.cardBranding, isValid)
    }

    private fun setViewsEnabled(view: android.view.View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                setViewsEnabled(view.getChildAt(i), enabled)
            }
        }
    }

    private fun qrEscapeWifi(text: String): String {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
    }

    private fun qrEscapeVCard(text: String): String {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }

    private fun qrEscapeMeCard(text: String): String {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(":", "\\:")
            .replace(",", "\\,")
    }
}
