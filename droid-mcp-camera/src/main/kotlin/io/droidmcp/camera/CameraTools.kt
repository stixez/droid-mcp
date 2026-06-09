package io.droidmcp.camera

import android.Manifest
import android.content.Context
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

    /** Reports [Manifest.permission.CAMERA] (the photo/video capture tools require it at runtime). */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA,
    )

    /** `true` when [Manifest.permission.CAMERA] is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
