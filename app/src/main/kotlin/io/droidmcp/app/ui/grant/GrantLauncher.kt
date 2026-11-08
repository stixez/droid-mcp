package io.droidmcp.app.ui.grant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.droidmcp.app.data.permissions.Capability
import io.droidmcp.app.data.permissions.GrantMethod
import io.droidmcp.app.service.ScreenCaptureService
import io.droidmcp.screenshot.DroidMcpCaptureService
import io.droidmcp.root.RootTools
import io.droidmcp.shizuku.ShizukuTools
import rikka.shizuku.Shizuku

/**
 * Turns "grant this capability" into the right Android flow: a permission dialog, a Settings
 * page, the screen-capture consent, or the Shizuku / root prompt. [onReturn] runs when the flow
 * ends (or when the user comes back from Settings, via the caller's resume refresh).
 */
@Composable
fun rememberGrantLauncher(onReturn: () -> Unit): (Capability) -> Unit {
    val context = LocalContext.current
    val latestOnReturn = rememberUpdatedState(onReturn)

    val requestedAt = remember { longArrayOf(0L) }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val activity = context.findActivity()
        val denied = results.filterValues { !it }.keys
        // Android stops showing the dialog after two refusals and answers "denied" at once. Send
        // the user to App info then, instead of leaving the button silently doing nothing. An
        // answer that took a moment came from a dialog the user dismissed, so ask again next time.
        val instant = SystemClock.elapsedRealtime() - requestedAt[0] < NO_DIALOG_MS
        val blocked = results.isNotEmpty() && results.values.none { it } && activity != null && instant &&
            denied.none { activity.shouldShowRequestPermissionRationale(it) }
        if (blocked) context.openAppInfo()
        latestOnReturn.value()
    }

    val screenCapture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            DroidMcpCaptureService.start(context, ScreenCaptureService::class.java, result.resultCode, data)
        }
        latestOnReturn.value()
    }

    // Shizuku answers through a listener rather than an activity result.
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> latestOnReturn.value() }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    return remember(context) {
        { capability: Capability ->
            when (val grant = capability.grant) {
                is GrantMethod.Runtime ->
                    if (grant.permissions.isEmpty()) {
                        latestOnReturn.value()
                    } else {
                        requestedAt[0] = SystemClock.elapsedRealtime()
                        runtime.launch(grant.permissions.toTypedArray())
                    }
                is GrantMethod.SystemSettings -> context.startSafely(grant.intent(context))
                GrantMethod.ScreenCapture -> {
                    screenCapture.launch(DroidMcpCaptureService.consentIntent(context))
                }
                GrantMethod.Shizuku -> when {
                    ShizukuTools.isShizukuReady() -> latestOnReturn.value()
                    runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> ShizukuTools.requestPermission(SHIZUKU_REQUEST)
                    else -> context.startSafely(ShizukuTools.installOrOpenIntent(context))
                }
                GrantMethod.Root -> RootTools.requestAccess { latestOnReturn.value() }
            }
        }
    }
}

private const val SHIZUKU_REQUEST = 0xCAFE

/** Open a Settings page, falling back to the main Settings screen on ROMs that lack it. */
fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    } catch (_: SecurityException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

/** This app's App info page, where "Allow restricted settings" and permission toggles live. */
fun Context.openAppInfo() {
    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A permission answer faster than this came without a dialog (Android had stopped asking). */
private const val NO_DIALOG_MS = 400L
