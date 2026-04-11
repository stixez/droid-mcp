package io.droidmcp.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.graphics.ImageFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
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
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CaptureVideoTool(private val context: Context) : McpTool {

    override val name = "capture_video"
    override val description = "Capture a video using the device camera"
    override val parameters = listOf(
        ToolParameter("duration_sec", "Recording duration in seconds (1-60, default 10)", ParameterType.INTEGER, required = false),
    )

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val durationSec = (params["duration_sec"] as? Number)?.toInt()?.coerceIn(1, 60) ?: 10

        val handlerThread = HandlerThread("VideoCapture").apply { start() }
        val handler = Handler(handlerThread.looper)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var mediaRecorder: MediaRecorder? = null
        var tempFile: File? = null

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
            ?: return@withContext ToolResult.error("No camera available")

            // Query supported video sizes
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val videoSizes = configMap?.getOutputSizes(MediaRecorder::class.java)
            val videoSize = videoSizes?.maxByOrNull { it.width * it.height }
                ?: return@withContext ToolResult.error("Cannot determine video resolution")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            tempFile = File(context.cacheDir, "VIDEO_$timestamp.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(10_000_000)
                setOutputFile(tempFile.absolutePath)
                prepare()
            }

            val recorderSurface = mediaRecorder.surface

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
                val outputConfig = OutputConfiguration(recorderSurface)
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

            val captureRequest = device!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(recorderSurface)
            }.build()

            session!!.setRepeatingRequest(captureRequest, null, handler)
            mediaRecorder.start()

            delay(durationSec * 1000L)

            mediaRecorder.stop()
            mediaRecorder.release()
            mediaRecorder = null // prevent double-release in finally

            session?.close()
            session = null
            device?.close()
            device = null

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
            tempFile = null

            ToolResult.success(mapOf(
                "file_path" to uri?.toString(),
                "duration_ms" to (durationSec * 1000L),
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to capture video: ${e.message}")
        } finally {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            session?.close()
            device?.close()
            handlerThread.quitSafely()
            tempFile?.delete()
        }
    }
}
