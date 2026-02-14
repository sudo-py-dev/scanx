package com.scanx.qrscanner

import android.content.Context

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BarcodeResult(
    val rawValue: String,
    val displayValue: String,
    val format: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val batchId: String? = null
) : Parcelable

class QrCodeAnalyzer(
    context: Context,
    private val onBarcodeDetected: (BarcodeResult) -> Unit,
    private val onAnalysisError: ((Exception) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private val appContext = context.applicationContext

    @Volatile
    private var isProcessing = false

    @Volatile
    var isPaused = false

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing || isPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true

        try {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcode = barcodes.first()
                        val result = BarcodeResult(
                            rawValue = barcode.rawValue ?: "",
                            displayValue = barcode.displayValue ?: barcode.rawValue ?: "",
                            format = getFormatName(barcode.format),
                            type = getTypeName(barcode.valueType)
                        )
                        onBarcodeDetected(result)
                    }
                }
                .addOnFailureListener { e ->
                    onAnalysisError?.invoke(e)
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        } catch (e: Exception) {
            isProcessing = false
            onAnalysisError?.invoke(e)
            imageProxy.close()
        }
    }

    fun resume() {
        isPaused = false
    }

    fun pause() {
        isPaused = true
    }

    fun close() {
        scanner.close()
    }

    private fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> appContext.getString(R.string.barcode_format_qr)
            Barcode.FORMAT_AZTEC -> appContext.getString(R.string.barcode_format_aztec)
            Barcode.FORMAT_DATA_MATRIX -> appContext.getString(R.string.barcode_format_data_matrix)
            Barcode.FORMAT_PDF417 -> appContext.getString(R.string.barcode_format_pdf417)
            Barcode.FORMAT_CODE_128 -> appContext.getString(R.string.barcode_format_code128)
            Barcode.FORMAT_CODE_39 -> appContext.getString(R.string.barcode_format_code39)
            Barcode.FORMAT_EAN_13 -> appContext.getString(R.string.barcode_format_ean13)
            Barcode.FORMAT_EAN_8 -> appContext.getString(R.string.barcode_format_ean8)
            Barcode.FORMAT_UPC_A -> appContext.getString(R.string.barcode_format_upca)
            Barcode.FORMAT_UPC_E -> appContext.getString(R.string.barcode_format_upce)
            else -> appContext.getString(R.string.barcode_format_unknown)
        }
    }

    private fun getTypeName(type: Int): String {
        return when (type) {
            Barcode.TYPE_URL -> appContext.getString(R.string.url)
            Barcode.TYPE_TEXT -> appContext.getString(R.string.text)
            Barcode.TYPE_EMAIL -> appContext.getString(R.string.email)
            Barcode.TYPE_PHONE -> appContext.getString(R.string.barcode_type_phone)
            Barcode.TYPE_SMS -> appContext.getString(R.string.sms)
            Barcode.TYPE_WIFI -> appContext.getString(R.string.wifi)
            Barcode.TYPE_GEO -> appContext.getString(R.string.barcode_type_location)
            Barcode.TYPE_CONTACT_INFO -> appContext.getString(R.string.barcode_type_contact)
            Barcode.TYPE_CALENDAR_EVENT -> appContext.getString(R.string.barcode_type_calendar)
            Barcode.TYPE_ISBN -> appContext.getString(R.string.barcode_type_isbn)
            Barcode.TYPE_PRODUCT -> appContext.getString(R.string.barcode_type_product)
            Barcode.TYPE_DRIVER_LICENSE -> appContext.getString(R.string.barcode_type_driver_license)
            else -> appContext.getString(R.string.text)
        }
    }
}
