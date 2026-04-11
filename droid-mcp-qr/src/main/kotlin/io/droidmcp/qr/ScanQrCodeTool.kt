package io.droidmcp.qr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ScanQrCodeTool(private val context: Context) : McpTool {

    override val name = "scan_qr_code"
    override val description = "Scan a QR code from an image file URI"
    override val parameters = listOf(
        ToolParameter("image_uri", "URI of the image file containing the QR code", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val imageUriStr = params["image_uri"]?.toString()
            ?: return@withContext ToolResult.error("image_uri is required")

        try {
            val imageUri = Uri.parse(imageUriStr)
            val image = InputImage.fromFilePath(context, imageUri)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val result = suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { barcodes -> cont.resume(barcodes) }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }

            if (result.isEmpty()) {
                return@withContext ToolResult.success(mapOf(
                    "raw_value" to null,
                    "format" to null,
                    "found" to false,
                ))
            }

            val barcode = result[0]
            ToolResult.success(mapOf(
                "raw_value" to barcode.rawValue,
                "format" to "QR_CODE",
                "found" to true,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to scan QR code: ${e.message}")
        }
    }
}
