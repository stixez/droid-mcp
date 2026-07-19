package io.droidmcp.camera

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the camera module: [GetCameraCapabilitiesTool], [TakePhotoTool], and [CaptureVideoTool].
 * Headless Camera2-based capture; the back camera is preferred when present. Requires camera hardware
 * (the manifest marks the feature non-required).
 */
object CameraTools {

    /** All tools in this module: [GetCameraCapabilitiesTool], [TakePhotoTool], [CaptureVideoTool]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetCameraCapabilitiesTool(context),
        TakePhotoTool(context),
        CaptureVideoTool(context),
    )

    /**
     * [Manifest.permission.CAMERA], always. Below API 29, [TakePhotoTool] and [CaptureVideoTool]
     * also need `WRITE_EXTERNAL_STORAGE` to insert into `MediaStore` — the scoped-storage
     * exemption for an app's own MediaStore inserts only applies on API 29+.
     */
    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.CAMERA)
        } else {
            listOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /** `true` when [Manifest.permission.CAMERA] is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
