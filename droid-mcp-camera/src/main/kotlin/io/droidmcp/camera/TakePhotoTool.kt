package io.droidmcp.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Base64
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TakePhotoTool(private val context: Context) : McpTool {

    override val name = "take_photo"
    override val description = "Capture a photo using the device camera (headless, no preview required)"
    override val parameters = listOf(
        ToolParameter("return_data", "Return image as base64 data (may be large)", ParameterType.BOOLEAN, required = false),
    )

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val returnData = params["return_data"] as? Boolean ?: false

        val handlerThread = HandlerThread("CameraCapture").apply { start() }
        val handler = Handler(handlerThread.looper)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var imageReader: ImageReader? = null

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

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)

            val imageBytes = withTimeoutOrNull(10_000L) {
                device = suspendCancellableCoroutine { cont ->
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) = cont.resume(camera)
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
                    val outputConfig = OutputConfiguration(imageReader!!.surface)
                    val sessionConfig = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(outputConfig),
                        Executors.newSingleThreadExecutor(),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) = cont.resume(s)
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                if (cont.isActive) cont.resumeWithException(RuntimeException("Session configuration failed"))
                            }
                        },
                    )
                    device!!.createCaptureSession(sessionConfig)
                }

                val captureRequest = device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader!!.surface)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    set(CaptureRequest.JPEG_QUALITY, 95.toByte())
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
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> os.write(imageBytes) } }

            val result = mutableMapOf<String, Any?>(
                "file_path" to uri?.toString(),
                "width" to size.width,
                "height" to size.height,
            )

            if (returnData) {
                if (imageBytes.size > 10_000_000) {
                    return@withContext ToolResult.error("Image too large for base64 return (${imageBytes.size} bytes). Use return_data=false")
                }
                result["image_data"] = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            }

            ToolResult.success(result)
        } catch (e: Exception) {
            ToolResult.error("Failed to take photo: ${e.message}")
        } finally {
            session?.close()
            device?.close()
            imageReader?.close()
            handlerThread.quitSafely()
        }
    }
}
