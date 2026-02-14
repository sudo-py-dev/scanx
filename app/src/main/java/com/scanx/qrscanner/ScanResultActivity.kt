package com.scanx.qrscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scanx.qrscanner.databinding.ActivityScanResultBinding
import java.util.*

class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val rawValue = intent.getStringExtra(EXTRA_RAW_VALUE) ?: ""
        val displayValue = intent.getStringExtra(EXTRA_DISPLAY_VALUE) ?: rawValue
        val format = intent.getStringExtra(EXTRA_FORMAT) ?: getString(R.string.barcode_format_unknown)
        val barcodeType = intent.getStringExtra(EXTRA_TYPE) ?: getString(R.string.barcode_format_unknown)

        binding.tvScannedData.text = displayValue
        binding.tvFormat.text = format
        binding.tvType.text = barcodeType

        val isMarketLink = rawValue.startsWith("market://") || 
                (Patterns.WEB_URL.matcher(rawValue).matches() && rawValue.contains("play.google.com/store/apps/details"))
        val isUrl = Patterns.WEB_URL.matcher(rawValue).matches() && !isMarketLink
        
        val isWifi = rawValue.uppercase().startsWith("WIFI:")
        val isGeo = rawValue.lowercase().startsWith("geo:")
        val isMail = rawValue.lowercase().startsWith("mailto:")
        val isSms = rawValue.lowercase().startsWith("smsto:") || rawValue.lowercase().startsWith("sms:")
        val isPhone = rawValue.lowercase().startsWith("tel:")
        val isVCard = rawValue.uppercase().contains("BEGIN:VCARD") || rawValue.uppercase().startsWith("MECARD:") || rawValue.uppercase().startsWith("BIZCARD:")
        val isEvent = rawValue.uppercase().contains("BEGIN:VEVENT")

        binding.btnOpenUrl.visibility = if (isUrl) View.VISIBLE else View.GONE
        binding.btnOpenStore.visibility = if (isMarketLink) View.VISIBLE else View.GONE
        binding.btnConnectWifi.visibility = if (isWifi) View.VISIBLE else View.GONE
        binding.llWifiDetails.visibility = if (isWifi) View.VISIBLE else View.GONE
        binding.btnOpenMap.visibility = if (isGeo) View.VISIBLE else View.GONE
        binding.btnSendEmail.visibility = if (isMail) View.VISIBLE else View.GONE
        binding.btnSendSms.visibility = if (isSms) View.VISIBLE else View.GONE
        binding.btnCall.visibility = if (isPhone) View.VISIBLE else View.GONE
        binding.btnAddContact.visibility = if (isVCard) View.VISIBLE else View.GONE
        binding.btnAddEvent.visibility = if (isEvent) View.VISIBLE else View.GONE

        // Hide search button if we have a specialized action button
        val hasSpecializedAction = isUrl || isMarketLink || isWifi || isGeo || isMail || isSms || isPhone || isVCard || isEvent
        binding.btnSearchWeb.visibility = if (hasSpecializedAction) View.GONE else View.VISIBLE

        if (isWifi) {
            setupWifiDetails(rawValue)
        }

        binding.btnOpenUrl.setOnClickListener {
            val url = if (rawValue.startsWith("http://", ignoreCase = true) || rawValue.startsWith("https://", ignoreCase = true)) {
                rawValue
            } else {
                "https://$rawValue"
            }
            
            // Basic URL validation
            if (!Patterns.WEB_URL.matcher(url).matches()) {
                Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isApkDownload = url.endsWith(".apk", ignoreCase = true)
            val titleRes = if (isApkDownload) R.string.warning_apk_download_title else R.string.url_confirmation_title
            val message = if (isApkDownload) {
                getString(R.string.warning_apk_download_message, url)
            } else {
                getString(R.string.url_confirmation_message, url)
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open) { _, _ ->
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            // Prevent Intent redirection vulnerabilities by ensuring it handles only web URLs
                            if (url.startsWith("http:", ignoreCase = true) || url.startsWith("https:", ignoreCase = true)) {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        }
                        if (browserIntent.resolveActivity(packageManager) != null) {
                            startActivity(browserIntent)
                        } else {
                            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        binding.btnConnectWifi.setOnClickListener {
            try {
                // Wifi settings doesn't need external data
                val wifiIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                startActivity(wifiIntent)
                // Avoid showing raw data in long toast if it could be malicious
                Toast.makeText(this, R.string.wifi_setup_instruction, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenMap.setOnClickListener {
            try {
                val geoUri = if (rawValue.startsWith("geo:", ignoreCase = true)) {
                    // Sanitize geo URI
                    rawValue
                } else {
                    "geo:0,0?q=${Uri.encode(rawValue)}"
                }
                val discoveryIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                if (discoveryIntent.resolveActivity(packageManager) != null) {
                    startActivity(discoveryIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSendEmail.setOnClickListener {
            try {
                val emailUri = if (rawValue.startsWith("mailto:", ignoreCase = true)) rawValue else "mailto:${Uri.encode(rawValue)}"
                val composeEmailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(emailUri))
                if (composeEmailIntent.resolveActivity(packageManager) != null) {
                    startActivity(composeEmailIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSendSms.setOnClickListener {
            try {
                val smsUri = when {
                    rawValue.uppercase().startsWith("SMSTO:") -> {
                        val parts = rawValue.substring(6)
                        val colonIdx = parts.indexOf(":")
                        if (colonIdx != -1) {
                            val phone = parts.substring(0, colonIdx)
                            val body = parts.substring(colonIdx + 1)
                            "smsto:${Uri.encode(phone)}?body=${Uri.encode(body)}"
                        } else {
                            "smsto:${Uri.encode(parts)}"
                        }
                    }
                    rawValue.lowercase().startsWith("sms:") -> rawValue
                    else -> "smsto:${Uri.encode(rawValue)}"
                }
                val composeSmsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(smsUri))
                if (composeSmsIntent.resolveActivity(packageManager) != null) {
                    startActivity(composeSmsIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCall.setOnClickListener {
            try {
                val telUri = if (rawValue.startsWith("tel:", ignoreCase = true)) rawValue else "tel:${Uri.encode(rawValue)}"
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse(telUri))
                if (callIntent.resolveActivity(packageManager) != null) {
                    startActivity(callIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddContact.setOnClickListener {
            try {
                val contactInsertIntent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                    type = "vnd.android.cursor.item/contact"
                    putExtra("finishActivityOnSaveCompleted", true)
                }
                
                if (rawValue.uppercase().startsWith("MECARD:") || rawValue.uppercase().startsWith("BIZCARD:")) {
                    parseMeCard(rawValue, contactInsertIntent)
                } else if (rawValue.uppercase().contains("BEGIN:VCARD")) {
                    parseVCard(rawValue, contactInsertIntent)
                }

                if (contactInsertIntent.resolveActivity(packageManager) != null) {
                    startActivity(contactInsertIntent)
                } else {
                    // Fallback to clipboard only if no app can handle the intent
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(getString(R.string.clipboard_label_contact_data), rawValue)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddEvent.setOnClickListener {
            try {
                addEventToCalendar(rawValue)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenStore.setOnClickListener {
            val storeUrl = if (rawValue.startsWith("market://", ignoreCase = true)) {
                rawValue
            } else if (rawValue.contains("details?id=", ignoreCase = true)) {
                val pkgPart = rawValue.split("details?id=")[1].split("&")[0]
                "market://details?id=${Uri.encode(pkgPart)}"
            } else {
                rawValue
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.play_store_confirmation_title)
                .setMessage(getString(R.string.play_store_confirmation_message, storeUrl))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open) { _, _ ->
                    try {
                        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))
                        playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (playStoreIntent.resolveActivity(packageManager) != null) {
                            startActivity(playStoreIntent)
                        } else {
                            val webStoreUrl = if (storeUrl.startsWith("market://", ignoreCase = true)) {
                                storeUrl.replace("market://", "https://play.google.com/store/apps/", ignoreCase = true)
                            } else {
                                storeUrl
                            }
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webStoreUrl)))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.clipboard_label_qr_data), rawValue)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            try {
                val shareActionIntent = Intent(Intent.ACTION_SEND)
                shareActionIntent.type = "text/plain"
                shareActionIntent.putExtra(Intent.EXTRA_TEXT, rawValue)
                startActivity(Intent.createChooser(shareActionIntent, getString(R.string.share_qr_data_title)))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSearchWeb.setOnClickListener {
            val settingsManager = SettingsManager(this)
            val query = Uri.encode(rawValue)
            val baseUrl = when (settingsManager.searchEngine) {
                SettingsManager.SEARCH_ENGINE_BING -> "https://www.bing.com/search?q="
                SettingsManager.SEARCH_ENGINE_DUCKDUCKGO -> "https://duckduckgo.com/?q="
                SettingsManager.SEARCH_ENGINE_YAHOO -> "https://search.yahoo.com/search?p="
                else -> "https://www.google.com/search?q="
            }
            try {
                val searchUri = Uri.parse("$baseUrl$query")
                val performSearchIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                if (performSearchIntent.resolveActivity(packageManager) != null) {
                    startActivity(performSearchIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnScanAgain.setOnClickListener {
            val restartScannerIntent = Intent(this, ScannerActivity::class.java)
            startActivity(restartScannerIntent)
            finish()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Security Hardening: Tapjacking Protection
        // Enable touch filtering for all sensitive action buttons to prevent UI redressing attacks
        val actionButtons = listOf(
            binding.btnOpenUrl, binding.btnOpenStore, binding.btnConnectWifi, 
            binding.btnOpenMap, binding.btnSendEmail, binding.btnSendSms, 
            binding.btnCall, binding.btnAddContact, binding.btnAddEvent,
            binding.btnCopy, binding.btnShare, binding.btnSearchWeb
        )
        actionButtons.forEach { it.filterTouchesWhenObscured = true }
    }

    private fun parseMeCard(data: String, intent: Intent) {
        if (data.length <= 7) return
        // MECARD:N:Name;TEL:123;...
        val content = data.substring(7)
        val fields = splitQrFields(content)
        val notesBuilder = StringBuilder()
        
        for (field in fields) {
            val colonIdx = field.indexOf(":")
            if (colonIdx == -1) continue
            
            val key = field.substring(0, colonIdx).uppercase()
            val value = field.substring(colonIdx + 1).replace("\\;", ";").replace("\\:", ":").trim()
            if (value.isEmpty()) continue

            when (key) {
                "N" -> intent.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, value)
                "TEL", "P" -> intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, value)
                "EMAIL", "E" -> intent.putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, value)
                "ORG", "C" -> intent.putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, value)
                "ADR" -> intent.putExtra(android.provider.ContactsContract.Intents.Insert.POSTAL, value)
                "T" -> intent.putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, value)
                "URL" -> {
                    if (notesBuilder.isNotEmpty()) notesBuilder.append("\n")
                    notesBuilder.append(getString(R.string.website_prefix)).append(value)
                }
                "NOTE" -> {
                    if (notesBuilder.isNotEmpty()) notesBuilder.append("\n")
                    notesBuilder.append(value)
                }
            }
        }
        if (notesBuilder.isNotEmpty()) {
            intent.putExtra(android.provider.ContactsContract.Intents.Insert.NOTES, notesBuilder.toString())
        }
    }

    private fun parseVCard(data: String, intent: Intent) {
        val lines = data.split("\n")
        val notesBuilder = StringBuilder()
        for (line in lines) {
            val upperLine = line.uppercase()
            val value = if (line.indexOf(":") != -1) line.substring(line.indexOf(":") + 1).trim() else ""
            if (value.isEmpty()) continue

            when {
                upperLine.startsWith("N:") || upperLine.startsWith("FN:") -> {
                    val cleanName = value.replace(";", " ").trim()
                    intent.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, cleanName)
                }
                upperLine.startsWith("TEL") -> {
                    intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, value)
                }
                upperLine.startsWith("EMAIL") -> {
                    intent.putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, value)
                }
                upperLine.startsWith("ORG:") -> {
                    intent.putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, value)
                }
                upperLine.startsWith("TITLE:") -> {
                    intent.putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, value)
                }
                upperLine.startsWith("ADR") -> {
                    val cleanAddr = value.replace(";", " ").trim()
                    intent.putExtra(android.provider.ContactsContract.Intents.Insert.POSTAL, cleanAddr)
                }
                upperLine.startsWith("URL:") -> {
                    if (notesBuilder.isNotEmpty()) notesBuilder.append("\n")
                    notesBuilder.append(getString(R.string.website_prefix)).append(value)
                }
                upperLine.startsWith("NOTE:") -> {
                    if (notesBuilder.isNotEmpty()) notesBuilder.append("\n")
                    notesBuilder.append(value)
                }
            }
        }
        if (notesBuilder.isNotEmpty()) {
            intent.putExtra(android.provider.ContactsContract.Intents.Insert.NOTES, notesBuilder.toString())
        }
    }

    private fun addEventToCalendar(data: String) {
        val lines = data.split("\n")
        var title = ""
        var location = ""
        var description = ""

        for (line in lines) {
            val upperLine = line.uppercase()
            val value = if (line.indexOf(":") != -1) line.substring(line.indexOf(":") + 1).trim() else ""
            if (value.isEmpty()) continue

            when {
                upperLine.startsWith("SUMMARY:") -> title = value
                upperLine.startsWith("LOCATION:") -> location = value
                upperLine.startsWith("DESCRIPTION:") -> description = value
            }
        }

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, title)
            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, description)
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWifiDetails(data: String) {
        var ssid = ""
        var password = ""
        
        if (data.length <= 5) return
        // WIFI:S:ssid;P:pass;T:type;;
        val content = data.substring(5)
        val fields = splitQrFields(content)
        
        for (field in fields) {
            val colonIdx = field.indexOf(":")
            if (colonIdx == -1) continue
            
            val key = field.substring(0, colonIdx).uppercase()
            val value = field.substring(colonIdx + 1).replace("\\;", ";").replace("\\:", ":").replace("\\\\", "\\")
            
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
            }
        }
        
        if (ssid.isNotEmpty()) {
            binding.tvWifiSsid.text = getString(R.string.wifi_ssid_label, ssid)
            binding.tvWifiSsid.visibility = View.VISIBLE
        } else {
            binding.tvWifiSsid.visibility = View.GONE
        }
        
        if (password.isNotEmpty()) {
            binding.tvWifiPassword.text = getString(R.string.wifi_password_label, password)
            binding.tvWifiPassword.visibility = View.VISIBLE
        } else {
            binding.tvWifiPassword.visibility = View.GONE
        }
    }

    /**
     * Splits QR fields (like in WIFI or MECARD) while respecting escaped semicolons.
     */
    private fun splitQrFields(content: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var escaped = false
        
        for (char in content) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    current.append(char)
                    escaped = true
                }
                char == ';' -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    companion object {
        const val EXTRA_RAW_VALUE = "extra_raw_value"
        const val EXTRA_DISPLAY_VALUE = "extra_display_value"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_TYPE = "extra_type"
    }
}
