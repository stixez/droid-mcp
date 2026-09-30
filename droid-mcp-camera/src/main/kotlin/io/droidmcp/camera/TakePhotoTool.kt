package io.droidmcp.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ExifInterface
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Captures a still JPEG headlessly (no on-screen preview) via Camera2, preferring the back-facing
 * camera at its maximum JPEG resolution, then saves it to `MediaStore` under `Pictures/droid-mcp`.
 *
 * Before the still capture a repeating preview request (into a small off-screen YUV reader) runs so
 * auto-exposure / auto-focus / auto-white-balance can converge — it waits until
 * `CONTROL_AE_STATE` reports converged / flash-required / locked (and AWB, when reported, has
 * converged), or ~1.5 seconds elapse — avoiding the dark, unfocused first frame a cold capture
 * produces. `JPEG_ORIENTATION` is set from the sensor orientation (assuming the device is held in
 * its natural orientation). The whole capture is bounded by a 10-second timeout.
 *
 * The MediaStore row is inserted with `IS_PENDING` on API 29+ and deleted if writing fails, so no
 * empty gallery entries are left behind.
 *
 * With `return_data = true` the photo is also returned base64-encoded — downscaled so its longest
 * side is at most `max_dimension` (default 1280), EXIF rotation applied, re-encoded as `format`
 * (`jpeg` default, or `png`) at `quality` (JPEG only, default 85). The saved gallery file is
 * always the full-resolution JPEG.
 *
 * Requires [android.Manifest.permission.CAMERA] and camera hardware; uses
 * [android.hardware.camera2.params.SessionConfiguration] (API 28+, met by the SDK's min API).
 *
 * Result keys: `file_path` (MediaStore content URI), `width`, `height` (full-resolution capture),
 * and with `return_data`: `image_data` (base64), `data_format`, `data_width`, `data_height`.
 */
class TakePhotoTool(private val context: Context) : McpTool {

    override val name = "take_photo"
    override val description = "Capture a photo using the device camera (headless, no preview required). Saves the full-resolution JPEG to the gallery; with return_data=true also returns a downscaled base64 copy (max_dimension, format, quality)."
    override val parameters = listOf(
        ToolParameter("return_data", "Return image as base64 data (downscaled; see max_dimension)", ParameterType.BOOLEAN, required = false),
        ToolParameter("max_dimension", "Longest side in pixels of the returned image (64-4096, default 1280). Only used with return_data.", ParameterType.INTEGER, required = false),
        ToolParameter("format", "Returned image format: 'jpeg' (default) or 'png'. Only used with return_data.", ParameterType.STRING, required = false),
        ToolParameter("quality", "JPEG quality 1-100 for the returned image (default 85). Ignored for PNG.", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val returnData = params["return_data"] as? Boolean ?: false
        val maxDimension = (params["max_dimension"] as? Number)?.toInt()?.coerceIn(64, 4096) ?: 1280
        val dataFormat = (params["format"] as? String)?.lowercase() ?: "jpeg"
        val compressFormat = when (dataFormat) {
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            "png" -> Bitmap.CompressFormat.PNG
            else -> return@withContext ToolResult.error("format must be 'jpeg' or 'png'")
        }
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 85

        val handlerThread = HandlerThread("CameraCapture").apply { start() }
        val handler = Handler(handlerThread.looper)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var imageReader: ImageReader? = null
        var previewReader: ImageReader? = null
        var sessionExecutor: java.util.concurrent.ExecutorService? = null

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
            ?: return@withContext ToolResult.error("No camera available")

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = configMap?.getOutputSizes(ImageFormat.JPEG)
            val size = jpegSizes?.maxByOrNull { it.width * it.height }
                ?: return@withContext ToolResult.error("Cannot determine camera resolution")
            val previewSize = pickPreviewSize(configMap?.getOutputSizes(ImageFormat.YUV_420_888))
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
            val afMode = if (CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in afModes) {
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            } else {
                null
            }

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            previewReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2).apply {
                // Drain preview frames so the repeating request never stalls on a full queue.
                setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, handler)
            }

            val imageBytes = withTimeoutOrNull(10_000L) {
                device = suspendCancellableCoroutine { cont ->
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        // If the overall withTimeoutOrNull already fired, this continuation is no
                        // longer active — a plain cont.resume(camera) would be silently discarded,
                        // leaking an opened CameraDevice that keeps the camera locked for every
                        // app (including this one) until process death. Close it instead.
                        override fun onOpened(camera: CameraDevice) {
                            if (cont.isActive) cont.resume(camera) else camera.close()
                        }
                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            if (cont.isActive) cont.resumeWithException(RuntimeException("Camera disconnected"))
                        }
                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            if (cont.isActive) cont.resumeWithException(RuntimeException("Camera error: $error"))
                        }
                    }, handler)
                }

                session = suspendCancellableCoroutine { cont ->
                    val outputs = listOf(
                        OutputConfiguration(previewReader!!.surface),
                        OutputConfiguration(imageReader!!.surface),
                    )
                    sessionExecutor = Executors.newSingleThreadExecutor()
                    val sessionConfig = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputs,
                        sessionExecutor,
                        object : CameraCaptureSession.StateCallback() {
                            // Same late-callback-after-timeout rationale as onOpened above.
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (cont.isActive) cont.resume(s) else s.close()
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                if (cont.isActive) cont.resumeWithException(RuntimeException("Session configuration failed"))
                            }
                        },
                    )
                    device!!.createCaptureSession(sessionConfig)
                }

                // 3A convergence: run a repeating preview until AE (and AWB, if reported)
                // settle, or the convergence budget runs out — then capture regardless.
                val converged = CompletableDeferred<Unit>()
                val previewRequest = device!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewReader!!.surface)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    afMode?.let { set(CaptureRequest.CONTROL_AF_MODE, it) }
                }.build()
                var frames = 0
                session!!.setRepeatingRequest(previewRequest, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                        frames++
                        if (is3aSettled(result, frames)) converged.complete(Unit)
                    }
                }, handler)
                withTimeoutOrNull(CONVERGENCE_TIMEOUT_MS) { converged.await() }
                runCatching { session!!.stopRepeating() }

                val captureRequest = device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader!!.surface)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    afMode?.let { set(CaptureRequest.CONTROL_AF_MODE, it) }
                    set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                    set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                }.build()

                suspendCancellableCoroutine<ByteArray?> { cont ->
                    imageReader!!.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        val buffer = image?.planes?.get(0)?.buffer
                        val data = buffer?.let { ByteArray(it.remaining()).also { arr -> it.get(arr) } }
                        image?.close()
                        if (cont.isActive) cont.resume(data)
                    }, handler)

                    session!!.capture(captureRequest, null, handler)
                }
            }

            if (imageBytes == null) {
                return@withContext ToolResult.error("Failed to capture photo (timeout)")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PHOTO_$timestamp.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/droid-mcp")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext ToolResult.error("Failed to create MediaStore entry for photo")
            val written = try {
                resolver.openOutputStream(uri)?.use { os -> os.write(imageBytes); true } ?: false
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            if (!written) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext ToolResult.error("Failed to open output stream for photo")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }

            val result = mutableMapOf<String, Any?>(
                "file_path" to uri.toString(),
                "width" to size.width,
                "height" to size.height,
            )

            if (returnData) {
                val encoded = encodeForReturn(imageBytes, maxDimension, compressFormat, quality)
                    ?: return@withContext ToolResult.error("Failed to decode captured photo for return_data")
                if (encoded.bytes.size > 10_000_000) {
                    return@withContext ToolResult.error("Image too large for base64 return (${encoded.bytes.size} bytes). Lower max_dimension or use return_data=false")
                }
                result["image_data"] = Base64.encodeToString(encoded.bytes, Base64.NO_WRAP)
                result["data_format"] = if (compressFormat == Bitmap.CompressFormat.PNG) "png" else "jpeg"
                result["data_width"] = encoded.width
                result["data_height"] = encoded.height
            }

            ToolResult.success(result)
        } catch (e: Exception) {
            ToolResult.error("Failed to take photo: ${e.message}")
        } finally {
            session?.close()
            device?.close()
            imageReader?.close()
            previewReader?.close()
            sessionExecutor?.shutdown()
            handlerThread.quitSafely()
        }
    }

    /**
     * True once AE reports converged / flash-required / locked (and AWB, when the device reports
     * it, is converged or locked). Devices that report no AE state (LEGACY) are treated as settled
     * after [LEGACY_SETTLE_FRAMES] frames.
     */
    private fun is3aSettled(result: CaptureResult, frames: Int): Boolean {
        val ae = result.get(CaptureResult.CONTROL_AE_STATE) ?: return frames >= LEGACY_SETTLE_FRAMES
        val aeOk = ae == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
            ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
            ae == CaptureResult.CONTROL_AE_STATE_LOCKED
        val awb = result.get(CaptureResult.CONTROL_AWB_STATE)
        val awbOk = awb == null ||
            awb == CaptureResult.CONTROL_AWB_STATE_CONVERGED ||
            awb == CaptureResult.CONTROL_AWB_STATE_LOCKED
        return aeOk && awbOk
    }

    /** Smallest-reasonable YUV preview stream (<= 640x480), for 3A only — never encoded. */
    private fun pickPreviewSize(sizes: Array<Size>?): Size =
        sizes?.filter { it.width <= 640 && it.height <= 480 }?.maxByOrNull { it.width * it.height }
            ?: sizes?.minByOrNull { it.width * it.height }
            ?: Size(640, 480)

    private class Encoded(val bytes: ByteArray, val width: Int, val height: Int)

    /**
     * Decode [jpeg] (subsampled toward [maxDimension] to bound memory), apply its EXIF rotation,
     * scale so the longest side is at most [maxDimension], and re-encode. All intermediate
     * bitmaps are recycled. Returns null when the JPEG can't be decoded.
     */
    private fun encodeForReturn(
        jpeg: ByteArray,
        maxDimension: Int,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Encoded? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(jpeg))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > maxDimension) maxDimension.toFloat() / longest else 1f

        val finalBitmap = if (rotation != 0 || scale < 1f) {
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }
        return try {
            val out = ByteArrayOutputStream()
            finalBitmap.compress(format, quality, out)
            Encoded(out.toByteArray(), finalBitmap.width, finalBitmap.height)
        } finally {
            if (finalBitmap !== decoded) finalBitmap.recycle()
            decoded.recycle()
        }
    }

    private companion object {
        const val CONVERGENCE_TIMEOUT_MS = 1_500L
        const val LEGACY_SETTLE_FRAMES = 10
    }
}
