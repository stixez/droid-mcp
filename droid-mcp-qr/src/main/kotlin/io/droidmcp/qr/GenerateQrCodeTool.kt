package io.droidmcp.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GenerateQrCodeTool : McpTool {

    override val name = "generate_qr_code"
    override val description = "Generate a QR code from text, returned as base64-encoded PNG"
    override val parameters = listOf(
        ToolParameter("text", "Text content to encode in the QR code", ParameterType.STRING, required = true),
        ToolParameter("size", "Image size in pixels (100-1000, default 300)", ParameterType.INTEGER, required = false),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val text = params["text"]?.toString()
            ?: return@withContext ToolResult.error("text is required")

        val size = (params["size"] as? Number)?.toInt()?.coerceIn(100, 1000) ?: 300

        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            bitmap.recycle()

            ToolResult.success(mapOf(
                "qr_image" to base64,
                "format" to "png",
                "size" to size,
            ))
        } catch (e: WriterException) {
            ToolResult.error("Failed to generate QR code: ${e.message}")
        }
    }
}
