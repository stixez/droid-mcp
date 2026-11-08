package io.droidmcp.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Copy [text]. Sensitive values (tokens) are flagged so Android hides them from the clipboard
 * preview. Returns true when the caller should confirm the copy itself, because Android 13+
 * already shows its own confirmation.
 */
fun Context.copyText(label: String, text: String, sensitive: Boolean): Boolean {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        // ClipDescription.EXTRA_IS_SENSITIVE is API 33; older versions honour the same key string.
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
