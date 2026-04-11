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

class ScanBarcodeTool(private val context: Context) : McpTool {

    override val name = "scan_barcode"
    override val description = "Scan a barcode from an image file URI. Supports EAN-13, UPC-A, CODE-128, etc."
    override val parameters = listOf(
        ToolParameter("image_uri", "URI of the image file containing the barcode", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val imageUriStr = params["image_uri"]?.toString()
            ?: return@withContext ToolResult.error("image_uri is required")

        try {
            val imageUri = Uri.parse(imageUriStr)
            val image = InputImage.fromFilePath(context, imageUri)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_E,
                )
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val result = try {
                suspendCancellableCoroutine { cont ->
                    scanner.process(image)
                        .addOnSuccessListener { barcodes -> cont.resume(barcodes) }
                        .addOnFailureListener { cont.resume(emptyList()) }
                }
            } finally {
                scanner.close()
            }

            if (result.isEmpty()) {
                return@withContext ToolResult.success(mapOf(
                    "raw_value" to null,
                    "format" to null,
                    "found" to false,
                ))
            }

            val barcode = result[0]
            val formatName = when (barcode.format) {
                Barcode.FORMAT_EAN_13 -> "EAN_13"
                Barcode.FORMAT_UPC_A -> "UPC_A"
                Barcode.FORMAT_CODE_128 -> "CODE_128"
                Barcode.FORMAT_CODE_39 -> "CODE_39"
                Barcode.FORMAT_EAN_8 -> "EAN_8"
                Barcode.FORMAT_UPC_E -> "UPC_E"
                else -> "UNKNOWN"
            }

            ToolResult.success(mapOf(
                "raw_value" to barcode.rawValue,
                "format" to formatName,
                "found" to true,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to scan barcode: ${e.message}")
        }
    }
}
