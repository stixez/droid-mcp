package io.droidmcp.camera

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object CameraTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetCameraCapabilitiesTool(context),
        TakePhotoTool(context),
        CaptureVideoTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
