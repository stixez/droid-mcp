package io.droidmcp.screenshot

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Holds the [MediaProjection] token obtained by the host Activity, plus the single
 * [VirtualDisplay] + [ImageReader] pair mirroring the screen for it.
 *
 * The host app must call [set] after the user grants screen-capture consent
 * (e.g. via `MediaProjectionManager.createScreenCaptureIntent()`).
 * [CaptureScreenTool] reads the token from here.
 *
 * [set] registers a per-projection [MediaProjection.Callback], required for two reasons: the
 * platform requires a callback to be registered before `createVirtualDisplay()` can be called at
 * all — without one, [CaptureScreenTool] throws `IllegalStateException` on every call; and
 * `onStop` lets this holder clear itself when the user revokes consent via the system's
 * screen-capture notification, instead of holding a dead token that looks valid but isn't. The
 * callback only clears state if the stopping projection is still the current one, so a late
 * `onStop` from a replaced projection can't wipe out its successor.
 *
 * **One virtual display per projection.** On Android 14+ (targetSdk 34+) a [MediaProjection]
 * may call `createVirtualDisplay` only once. The holder therefore creates the virtual display
 * lazily on the first capture and keeps it (and its reader) alive for the projection's
 * lifetime; each capture reads the most recent frame. Everything is released when the
 * projection stops, on [clear], or when [set] replaces it.
 *
 * @property projection the active projection, or `null` if consent has not been
 *   granted (or has been cleared).
 */
object MediaProjectionHolder {
    @Volatile
    var projection: MediaProjection? = null
        private set

    private val lock = Any()
    private var callback: MediaProjection.Callback? = null
    private var capture: ProjectionCapture? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Stores [mediaProjection] as the active projection token and registers its callback. Any
     * previous projection is unregistered, its virtual display released, and it is stopped.
     * Calling again with the current projection is a no-op.
     */
    fun set(mediaProjection: MediaProjection) {
        synchronized(lock) {
            if (projection === mediaProjection) return
            teardownLocked(stop = true)
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    synchronized(lock) {
                        if (projection === mediaProjection) teardownLocked(stop = false)
                    }
                }
            }
            // Explicit main-looper handler: a null handler means "the calling thread's
            // looper", which crashes when set() is called from a non-looper thread.
            mediaProjection.registerCallback(cb, mainHandler)
            callback = cb
            projection = mediaProjection
        }
    }

    /** Stops and clears the active projection, releasing its virtual display. */
    fun clear() {
        synchronized(lock) { teardownLocked(stop = true) }
    }

    /**
     * Return the capture pipeline for the current projection, creating its virtual display on
     * first use and resizing it if the display geometry changed.
     *
     * @return null when no projection is held.
     */
    internal fun captureFor(width: Int, height: Int, densityDpi: Int): ProjectionCapture? {
        synchronized(lock) {
            val mp = projection ?: return null
            val existing = capture
            if (existing != null) {
                existing.ensureSize(width, height, densityDpi)
                return existing
            }
            return ProjectionCapture.create(mp, width, height, densityDpi).also { capture = it }
        }
    }

    private fun teardownLocked(stop: Boolean) {
        val mp = projection
        capture?.release()
        capture = null
        callback?.let { cb -> mp?.let { runCatching { it.unregisterCallback(cb) } } }
        callback = null
        projection = null
        if (stop) mp?.let { runCatching { it.stop() } }
    }
}

/**
 * The long-lived [VirtualDisplay] + [ImageReader] for one projection. A background listener
 * continuously drains the reader, keeping only the newest [Image] so the producer never stalls
 * on a full queue and a capture always sees the current screen (even when the screen is static
 * and no new frames arrive).
 */
internal class ProjectionCapture private constructor(
    private val virtualDisplay: VirtualDisplay,
    private var reader: ImageReader,
    var width: Int,
    var height: Int,
    private var densityDpi: Int,
    private val thread: HandlerThread,
) {
    private val frameLock = Any()
    private var latest: Image? = null

    init {
        attachListener(reader)
    }

    private fun attachListener(r: ImageReader) {
        r.setOnImageAvailableListener({ rd ->
            val img = runCatching { rd.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            synchronized(frameLock) {
                if (rd !== reader) {
                    img.close()
                } else {
                    latest?.close()
                    latest = img
                }
            }
        }, Handler(thread.looper))
    }

    /**
     * Run [block] against the newest frame while holding it (the frame stays owned by this
     * object and must not be closed by [block]).
     *
     * @return null when no frame has arrived yet.
     */
    fun <T> withLatestFrame(block: (Image) -> T): T? = synchronized(frameLock) {
        latest?.let(block)
    }

    /** Resize the virtual display (and swap in a matching reader) if the geometry changed. */
    fun ensureSize(newWidth: Int, newHeight: Int, newDensity: Int) {
        if (newWidth == width && newHeight == height && newDensity == densityDpi) return
        val newReader = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, MAX_IMAGES)
        val old: ImageReader
        synchronized(frameLock) {
            old = reader
            reader = newReader
            latest?.close()
            latest = null
            width = newWidth
            height = newHeight
            densityDpi = newDensity
        }
        attachListener(newReader)
        virtualDisplay.resize(newWidth, newHeight, newDensity)
        virtualDisplay.surface = newReader.surface
        old.setOnImageAvailableListener(null, null)
        old.close()
    }

    fun release() {
        runCatching { virtualDisplay.release() }
        synchronized(frameLock) {
            latest?.close()
            latest = null
            reader.setOnImageAvailableListener(null, null)
            reader.close()
        }
        thread.quitSafely()
    }

    companion object {
        /** One held "latest" image + headroom for acquireLatestImage + the producer. */
        private const val MAX_IMAGES = 3

        fun create(mp: MediaProjection, width: Int, height: Int, densityDpi: Int): ProjectionCapture {
            val thread = HandlerThread("droid-mcp-screenshot").apply { start() }
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES)
            val vd = try {
                mp.createVirtualDisplay(
                    "droid-mcp-screenshot",
                    width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface, null, null,
                ) ?: throw IllegalStateException("createVirtualDisplay returned null")
            } catch (e: Exception) {
                reader.close()
                thread.quitSafely()
                throw e
            }
            return ProjectionCapture(vd, reader, width, height, densityDpi, thread)
        }
    }
}
