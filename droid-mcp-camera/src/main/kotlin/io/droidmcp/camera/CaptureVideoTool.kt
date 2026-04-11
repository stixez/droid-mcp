package io.droidmcp.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

class CaptureVideoTool(private val context: Context) : McpTool {

    override val name = "capture_video"
    override val description = "Capture a video using the device camera"
    override val parameters = listOf(
        ToolParameter("duration_sec", "Recording duration in seconds (1-60, default 10)", ParameterType.INTEGER, required = false),
    )

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val durationSec = (params["duration_sec"] as? Number)?.toInt()?.coerceIn(1, 60) ?: 10

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
            ?: return@withContext ToolResult.error("No camera available")

            val handlerThread = HandlerThread("VideoCapture").apply { start() }
            val handler = Handler(handlerThread.looper)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val tempFile = File(context.cacheDir, "VIDEO_$timestamp.mp4")

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(1920, 1080)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(10_000_000)
                setOutputFile(tempFile.absolutePath)
                prepare()
            }

            val recorderSurface = mediaRecorder.surface

            val device = suspendCancellableCoroutine<CameraDevice> { cont ->
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) = cont.resume(camera)
                    override fun onDisconnected(camera: CameraDevice) { camera.close() }
                    override fun onError(camera: CameraDevice, error: Int) { camera.close() }
                }, handler)
            }

            val session = suspendCancellableCoroutine<CameraCaptureSession> { cont ->
                device.createCaptureSession(
                    listOf(recorderSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) = cont.resume(s)
                        override fun onConfigureFailed(s: CameraCaptureSession) { device.close() }
                    },
                    handler,
                )
            }

            val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(recorderSurface)
            }.build()

            session.setRepeatingRequest(captureRequest, null, handler)
            mediaRecorder.start()

            delay(durationSec * 1000L)

            mediaRecorder.stop()
            mediaRecorder.release()
            session.close()
            device.close()
            handlerThread.quitSafely()

            // Copy to MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "VIDEO_$timestamp.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/droid-mcp")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { destUri ->
                context.contentResolver.openOutputStream(destUri)?.use { os ->
                    tempFile.inputStream().use { it.copyTo(os) }
                }
            }
            tempFile.delete()

            ToolResult.success(mapOf(
                "file_path" to uri?.toString(),
                "duration_ms" to (durationSec * 1000L),
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to capture video: ${e.message}")
        }
    }
}
