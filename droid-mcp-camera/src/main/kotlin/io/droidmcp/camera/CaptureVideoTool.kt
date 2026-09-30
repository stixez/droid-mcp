package io.droidmcp.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.graphics.ImageFormat
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.reportProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How long to wait for the camera's first recorded frame before giving up. */
private const val FIRST_FRAME_TIMEOUT_MS = 3_000L

/** Extra time allowed after the requested duration for the encoder to write its first sample. */
private const val ENCODER_GRACE_MS = 3_000L

/**
 * Records an H.264/MP4 video (video only — no audio track) for `duration_sec` seconds (clamped 1–60,
 * default 10) via Camera2 + [MediaRecorder], preferring the back-facing camera, then copies the file
 * into `MediaStore` under `Movies/droid-mcp`.
 *
 * The recording size is the largest `MediaRecorder` output size that fits within 1920x1080 (either
 * orientation) — sensor-maximum sizes are often not encodable by the device's H.264 encoder. The
 * bit rate comes from the matching `CamcorderProfile` / `EncoderProfiles` (1080p/720p/480p) when
 * the device defines one, else is derived from the frame size (~0.1 bits per pixel per frame at
 * 30fps, clamped 1–12 Mbps).
 *
 * Requires [android.Manifest.permission.CAMERA] and camera hardware; uses
 * [android.hardware.camera2.params.SessionConfiguration] (API 28+, met by the SDK's min API). No audio
 * is captured, so no `RECORD_AUDIO` permission is needed.
 *
 * Result keys: `file_path` (MediaStore content URI), `duration_ms`, `width`, `height`, `bit_rate`.
 */
class CaptureVideoTool(private val context: Context) : McpTool {

    override val name = "capture_video"
    override val description = "Capture a video using the device camera"
    override val parameters = listOf(
        ToolParameter("duration_sec", "Recording duration in seconds (1-60, default 10)", ParameterType.INTEGER, required = false, minimum = 1.0, maximum = 60.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val durationSec = (params["duration_sec"] as? Number)?.toInt()?.coerceIn(1, 60) ?: 10

        val handlerThread = HandlerThread("VideoCapture").apply { start() }
        val handler = Handler(handlerThread.looper)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var mediaRecorder: MediaRecorder? = null
        var tempFile: File? = null
        var sessionExecutor: java.util.concurrent.ExecutorService? = null

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
            val videoSize = videoSizes?.let { pickVideoSize(it) }
                ?: return@withContext ToolResult.error("Cannot determine video resolution")
            val bitRate = bitRateFor(cameraId, videoSize)

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
                setVideoEncodingBitRate(bitRate)
                setOutputFile(tempFile.absolutePath)
                prepare()
            }

            val recorderSurface = mediaRecorder.surface

            // Bounded, unlike the recording itself (which legitimately runs up to 60s): a
            // camera-service hang or a concurrent-camera-user OEM quirk would otherwise suspend
            // this call indefinitely, holding the HandlerThread and MediaRecorder forever.
            val setupOk = withTimeoutOrNull(10_000L) {
                device = suspendCancellableCoroutine { cont ->
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        // If the timeout already fired, resume is silently discarded — close
                        // the device instead of leaking a locked-but-unreferenced camera.
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
                    val outputConfig = OutputConfiguration(recorderSurface)
                    sessionExecutor = Executors.newSingleThreadExecutor()
                    val sessionConfig = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(outputConfig),
                        sessionExecutor,
                        object : CameraCaptureSession.StateCallback() {
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
                true
            }
            if (setupOk == null) {
                return@withContext ToolResult.error("Failed to set up camera for recording (timeout)")
            }

            val captureRequest = device!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(recorderSurface)
            }.build()

            // MediaRecorder.stop() throws if no frame was recorded, and a camera that was just
            // reopened can take over a second to deliver one. Count the duration from the first
            // completed frame instead of from start().
            val firstFrame = CompletableDeferred<Unit>()
            val frameCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    firstFrame.complete(Unit)
                }
            }
            mediaRecorder.start()
            session!!.setRepeatingRequest(captureRequest, frameCallback, handler)
            withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { firstFrame.await() }
                ?: return@withContext ToolResult.error("Camera delivered no frames within ${FIRST_FRAME_TIMEOUT_MS / 1000}s; try again")
            val output: File = tempFile
            val sizeAtFirstFrame = output.length()

            for (second in 1..durationSec) {
                delay(1000L)
                reportProgress(second.toDouble(), durationSec.toDouble(), "Recording")
            }

            // The encoder can lag the camera (slow software encoders need over a second to emit
            // their first sample). Stopping before any sample reached the file makes stop() throw,
            // so give it a bounded grace period to write something first.
            withTimeoutOrNull(ENCODER_GRACE_MS) {
                while (output.length() <= sizeAtFirstFrame) delay(100)
            }
            try {
                mediaRecorder.stop()
            } catch (e: RuntimeException) {
                return@withContext ToolResult.error("Recording produced no video data (${e.message}); try again")
            }
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
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext ToolResult.error("Failed to create MediaStore entry for video")
            val written = try {
                resolver.openOutputStream(uri)?.use { os ->
                    tempFile.inputStream().use { it.copyTo(os) }
                    true
                } ?: false
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            if (!written) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext ToolResult.error("Failed to open output stream for video")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            tempFile.delete()
            tempFile = null

            ToolResult.success(mapOf(
                "file_path" to uri.toString(),
                "duration_ms" to (durationSec * 1000L),
                "width" to videoSize.width,
                "height" to videoSize.height,
                "bit_rate" to bitRate,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to capture video: ${e.message}")
        } finally {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            session?.close()
            device?.close()
            sessionExecutor?.shutdown()
            handlerThread.quitSafely()
            tempFile?.delete()
        }
    }

    /** Largest size fitting 1920x1080 in either orientation; the smallest size if none fits. */
    private fun pickVideoSize(sizes: Array<Size>): Size? =
        sizes.filter { maxOf(it.width, it.height) <= 1920 && minOf(it.width, it.height) <= 1080 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }

    /**
     * Bit rate for [size]: the device's camcorder/encoder profile for the matching quality
     * level when available, else ~0.1 bpp at 30fps clamped to 1–12 Mbps.
     */
    private fun bitRateFor(cameraId: String, size: Size): Int {
        val pixels = size.width * size.height
        val quality = when {
            pixels >= 1920 * 1080 -> CamcorderProfile.QUALITY_1080P
            pixels >= 1280 * 720 -> CamcorderProfile.QUALITY_720P
            pixels >= 720 * 480 -> CamcorderProfile.QUALITY_480P
            else -> null
        }
        val fromProfile = quality?.let { q ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    CamcorderProfile.getAll(cameraId, q)?.videoProfiles?.firstOrNull { it != null }?.bitrate
                } else {
                    val id = cameraId.toIntOrNull()
                    if (id != null && CamcorderProfile.hasProfile(id, q)) {
                        @Suppress("DEPRECATION")
                        CamcorderProfile.get(id, q).videoBitRate
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }
        return fromProfile?.takeIf { it > 0 }
            ?: (pixels.toLong() * 30L / 10L).toInt().coerceIn(1_000_000, 12_000_000)
    }
}
