package com.scanx.qrscanner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scanx.qrscanner.databinding.ActivityScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.ArrayList
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isFlashOn = false
    private var camera: androidx.camera.core.Camera? = null
    private var analyzer: QrCodeAnalyzer? = null
    private var toneGenerator: ToneGenerator? = null

    // Multi-scan mode
    private var isMultiScanMode = false
    private var scannedResults = mutableListOf<BarcodeResult>()
    private var isProcessingImage = false
    private var isPickerOpen = false
    private val scannedValues = mutableSetOf<String>() // deduplicate
    private lateinit var historyRepository: HistoryRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var scanner: BarcodeScanner

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        isPickerOpen = false
        if (uris.isNotEmpty()) {
            processPickedImages(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        historyRepository = HistoryRepository(this)
        settingsManager = SettingsManager(this)
        scanner = BarcodeScanning.getClient()
        
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }

        if (savedInstanceState != null) {
            isMultiScanMode = savedInstanceState.getBoolean(KEY_MULTI_SCAN_MODE, false)
            @Suppress("DEPRECATION")
            val restoredResults = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelableArrayList(KEY_SCANNED_RESULTS, BarcodeResult::class.java)
            } else {
                savedInstanceState.getParcelableArrayList(KEY_SCANNED_RESULTS)
            }
            if (restoredResults != null) {
                scannedResults.addAll(restoredResults)
                scannedValues.addAll(restoredResults.map { it.rawValue })
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnClose.setOnClickListener { finish() }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnMultiScan.setOnClickListener {
            toggleMultiScanMode()
        }

        binding.btnGallery.setOnClickListener {
            if (!isPickerOpen && !isProcessingImage) {
                isPickerOpen = true
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        binding.btnViewHistory.setOnClickListener {
            openHistory()
        }

        binding.chipLatestResult.setOnClickListener {
            if (scannedResults.isNotEmpty()) {
                openHistory()
            }
        }

        binding.zoomSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                camera?.cameraControl?.setLinearZoom(value)
            }
        }

        updateMultiScanUI()
        startCamera()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMultiScanMode && scannedResults.isNotEmpty()) {
                    MaterialAlertDialogBuilder(this@ScannerActivity)
                        .setTitle(R.string.exit_multi_scan_title)
                        .setMessage(getString(R.string.exit_multi_scan_message, scannedResults.size))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.exit) { _, _ ->
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Security Hardening: Tapjacking Protection
        binding.btnGallery.filterTouchesWhenObscured = true
        binding.btnViewHistory.filterTouchesWhenObscured = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_MULTI_SCAN_MODE, isMultiScanMode)
        outState.putParcelableArrayList(KEY_SCANNED_RESULTS, ArrayList(scannedResults))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                analyzer = QrCodeAnalyzer(
                    this,
                    onBarcodeDetected = { result ->
                        runOnUiThread {
                            handleBarcodeResult(result)
                        }
                    },
                    onAnalysisError = { _ ->
                        runOnUiThread {
                            // Security Hardening: Avoid logging raw noise to prevent data leaks in logs
                            // Log.d(TAG, "Analysis noise: ${exception.message}")
                        }
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer!!)
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Initialize Zoom
                camera?.cameraInfo?.zoomState?.observe(this) { state ->
                    binding.zoomSlider.visibility = View.VISIBLE
                    // Avoid recursive updates if the user is sliding
                    if (!binding.zoomSlider.isFocused) {
                        binding.zoomSlider.value = state.linearZoom
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                Toast.makeText(this, getString(R.string.error_start_camera, e.message), Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleBarcodeResult(result: BarcodeResult) {
        if (isMultiScanMode) {
            handleMultiScanResult(result)
        } else {
            handleSingleScanResult(result)
        }
    }

    private fun handleSingleScanResult(result: BarcodeResult) {
        // Stop analysis to prevent multiple detections
        analyzer?.pause()
        triggerFeedback()

        // Persist
        lifecycleScope.launch {
            historyRepository.saveResult(result)
        }

        val intent = Intent(this, ScanResultActivity::class.java).apply {
            putExtra(ScanResultActivity.EXTRA_RAW_VALUE, result.rawValue)
            putExtra(ScanResultActivity.EXTRA_DISPLAY_VALUE, result.displayValue)
            putExtra(ScanResultActivity.EXTRA_FORMAT, result.format)
            putExtra(ScanResultActivity.EXTRA_TYPE, result.type)
        }
        startActivity(intent)
        finish()
    }

    private fun handleMultiScanResult(result: BarcodeResult) {
        // Deduplicate: skip if already scanned
        if (scannedValues.contains(result.rawValue)) return

        scannedValues.add(result.rawValue)
        scannedResults.add(result)
        
        // Persist
        lifecycleScope.launch {
            historyRepository.saveResult(result)
        }

        triggerFeedback()

        // Pause briefly to avoid rapid-fire detections of the same code
        analyzer?.pause()

        // Show latest result chip
        showLatestResult(result)

        // Update counter
        updateScanCounter()

        // Resume scanning after a short delay
        binding.root.postDelayed({
            analyzer?.resume()
        }, MULTI_SCAN_DELAY_MS)
    }

    private fun showLatestResult(result: BarcodeResult) {
        binding.chipLatestResult.visibility = View.VISIBLE
        binding.chipLatestResult.text = if (result.displayValue.length > 35) {
            result.displayValue.take(35) + "…"
        } else {
            result.displayValue
        }

        // Pop-in animation
        binding.chipLatestResult.alpha = 0f
        binding.chipLatestResult.scaleX = 0.8f
        binding.chipLatestResult.scaleY = 0.8f
        binding.chipLatestResult.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250)
            .start()
    }

    private fun updateScanCounter() {
        val count = scannedResults.size
        binding.tvScanCount.visibility = View.VISIBLE
        binding.tvScanCount.text = getString(R.string.scanned_count_label, count.toString())

        binding.btnViewHistory.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun toggleMultiScanMode() {
        isMultiScanMode = !isMultiScanMode
        updateMultiScanUI()
        // Removed automatic redirection to history
    }

    private fun updateMultiScanUI() {
        if (isMultiScanMode) {
            binding.btnMultiScan.setIconResource(R.drawable.ic_multi_scan_on)
            binding.tvInstruction.text = getString(R.string.multi_scan_instruction)
            binding.tvScanCount.visibility = View.VISIBLE
            binding.tvScanCount.text = getString(R.string.scanned_count_label, scannedResults.size.toString())
        } else {
            binding.btnMultiScan.setIconResource(R.drawable.ic_multi_scan_off)
            binding.tvInstruction.text = getString(R.string.scan_instruction)
            binding.tvScanCount.visibility = View.GONE
            binding.chipLatestResult.visibility = View.GONE
            binding.btnViewHistory.visibility = View.GONE
        }
    }

    private fun openHistory() {
        val intent = Intent(this, ScanHistoryActivity::class.java)
        startActivity(intent)
    }

    private fun toggleFlash() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                isFlashOn = !isFlashOn
                cam.cameraControl.enableTorch(isFlashOn)
                binding.btnFlash.setIconResource(
                    if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                )
            }
        }
    }

    private fun triggerFeedback() {
        if (!settingsManager.isBeepEnabled) return

        try {
            // Haptic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }

            // Audio (Beep)
            val toneType = when (settingsManager.beepStyle) {
                SettingsManager.BEEP_ELECTRONIC -> ToneGenerator.TONE_DTMF_D
                SettingsManager.BEEP_NOTIFICATION -> ToneGenerator.TONE_PROP_BEEP
                SettingsManager.BEEP_BLIP -> ToneGenerator.TONE_CDMA_PIP
                else -> ToneGenerator.TONE_SUP_PIP
            }
            toneGenerator?.startTone(toneType, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Feedback failed", e)
        }
    }


    private fun processPickedImages(uris: List<Uri>) {
        if (isProcessingImage) return
        isProcessingImage = true
        
        val batchId = "batch_${System.currentTimeMillis()}"
        var currentIndex = 0
        val total = uris.size

        fun processNext() {
            if (isFinishing || isDestroyed) {
                isProcessingImage = false
                return
            }

            if (currentIndex >= total) {
                runOnUiThread {
                    isProcessingImage = false
                    Toast.makeText(this, R.string.scan_successful, Toast.LENGTH_SHORT).show()
                    binding.tvScanCount.visibility = View.GONE
                }
                return
            }

            val uri = uris[currentIndex]
            currentIndex++

            // Update UI progress if multi-scan or just show toast
            runOnUiThread {
                binding.tvScanCount.visibility = View.VISIBLE
                binding.tvScanCount.text = getString(R.string.scan_progress_format, scannedResults.size, currentIndex, total)
            }

            cameraExecutor.execute {
                try {
                    val inputImage = InputImage.fromFilePath(this, uri)
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            runOnUiThread {
                                if (barcodes.isNotEmpty()) {
                                    // Take first code from image
                                    val barcode = barcodes.first()
                                    val result = BarcodeResult(
                                        rawValue = barcode.rawValue ?: "",
                                        displayValue = barcode.displayValue ?: barcode.rawValue ?: "",
                                        format = getFormatName(barcode.format),
                                        type = getTypeName(barcode.valueType),
                                        batchId = batchId
                                    )
                                    handleBarcodeResult(result)
                                }
                                processNext()
                            }
                        }
                        .addOnFailureListener { e ->
                            runOnUiThread {
                                Log.e(TAG, "Batch processing failed for $uri", e)
                                processNext()
                            }
                        }
                } catch (e: Exception) {
                    runOnUiThread {
                        Log.e(TAG, "Error loading image from URI: $uri", e)
                        processNext()
                    }
                }
            }
        }

        processNext()
    }

    private fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> getString(R.string.barcode_format_qr)
            Barcode.FORMAT_AZTEC -> getString(R.string.barcode_format_aztec)
            Barcode.FORMAT_DATA_MATRIX -> getString(R.string.barcode_format_data_matrix)
            Barcode.FORMAT_PDF417 -> getString(R.string.barcode_format_pdf417)
            Barcode.FORMAT_CODE_128 -> getString(R.string.barcode_format_code128)
            Barcode.FORMAT_CODE_39 -> getString(R.string.barcode_format_code39)
            Barcode.FORMAT_EAN_13 -> getString(R.string.barcode_format_ean13)
            Barcode.FORMAT_EAN_8 -> getString(R.string.barcode_format_ean8)
            Barcode.FORMAT_UPC_A -> getString(R.string.barcode_format_upca)
            Barcode.FORMAT_UPC_E -> getString(R.string.barcode_format_upce)
            else -> getString(R.string.barcode_format_unknown)
        }
    }

    private fun getTypeName(type: Int): String {
        return when (type) {
            Barcode.TYPE_URL -> getString(R.string.url)
            Barcode.TYPE_TEXT -> getString(R.string.text)
            Barcode.TYPE_EMAIL -> getString(R.string.email)
            Barcode.TYPE_PHONE -> getString(R.string.barcode_type_phone)
            Barcode.TYPE_SMS -> getString(R.string.sms)
            Barcode.TYPE_WIFI -> getString(R.string.wifi)
            Barcode.TYPE_GEO -> getString(R.string.barcode_type_location)
            Barcode.TYPE_CONTACT_INFO -> getString(R.string.barcode_type_contact)
            Barcode.TYPE_CALENDAR_EVENT -> getString(R.string.barcode_type_calendar)
            Barcode.TYPE_ISBN -> getString(R.string.barcode_type_isbn)
            Barcode.TYPE_PRODUCT -> getString(R.string.barcode_type_product)
            Barcode.TYPE_DRIVER_LICENSE -> getString(R.string.barcode_type_driver_license)
            else -> getString(R.string.text)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            toneGenerator?.release()
            toneGenerator = null
            scanner.close()
            analyzer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    companion object {
        private const val TAG = "ScannerActivity"
        private const val KEY_MULTI_SCAN_MODE = "key_multi_scan_mode"
        private const val KEY_SCANNED_RESULTS = "key_scanned_results"
        private const val MULTI_SCAN_DELAY_MS = 1200L
    }
}
