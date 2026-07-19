package io.droidmcp.core

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime-permission checks shared by every module's provider object. The SDK never
 * *requests* permissions (the host app owns its permission UX) — it only inspects what
 * has already been granted, so a tool can decline gracefully.
 */
object PermissionHelper {

    /** True only if every permission in [permissions] is currently granted to the app. */
    fun hasPermissions(context: Context, permissions: List<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** The subset of [permissions] not currently granted — empty when all are held. */
    fun missingPermissions(context: Context, permissions: List<String>): List<String> =
        permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
}
