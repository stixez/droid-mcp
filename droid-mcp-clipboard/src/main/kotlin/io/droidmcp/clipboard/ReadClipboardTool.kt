package io.droidmcp.clipboard

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reads the system primary clip via `ClipboardManager`, coercing the first item to text. No
 * permissions required. Output always includes `has_content` (whether a primary clip exists),
 * `is_text` (clip MIME type starts with `text/`), and `text` (coerced string, or null when empty).
 *
 * On API 29+ the OS only lets the app with input focus (or the default IME) read the clipboard;
 * everyone else silently gets an empty clip. So when the clip reads back empty *and* the host app
 * is neither foreground nor the default IME, the tool returns an error instead of a misleading
 * `has_content: false` — the clipboard may well have content that simply isn't readable.
 */
class ReadClipboardTool(private val context: Context) : McpTool {

    override val name = "read_clipboard"
    override val description = "Read the current clipboard content. On Android 10+ this only works while the host app is in the foreground (or is the default keyboard)."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = if (clipboard.hasPrimaryClip()) clipboard.primaryClip else null
        if (clip == null || clip.itemCount == 0) {
            if (!canReadClipboard()) {
                return ToolResult.error(BACKGROUND_ERROR)
            }
            return ToolResult.success(mapOf(
                "has_content" to false,
                "is_text" to false,
                "text" to null,
            ))
        }

        val item = clip.getItemAt(0)
        val text = item?.coerceToText(context)?.toString()
        val isText = clip.description.hasMimeType("text/*")

        return ToolResult.success(mapOf(
            "has_content" to true,
            "is_text" to isText,
            "text" to text,
        ))
    }

    /**
     * Best-effort check for the API 29+ clipboard-read exemptions a library can observe: the
     * process is foreground/visible, or this app is the default input method. Always true below 29.
     */
    private fun canReadClipboard(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        ) {
            return true
        }
        val defaultIme = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (e: Exception) {
            null
        }
        return defaultIme?.substringBefore('/') == context.packageName
    }

    private companion object {
        const val BACKGROUND_ERROR =
            "Clipboard is not readable: since Android 10 only the foreground app (with input focus) " +
                "or the default keyboard can read the clipboard. Bring the host app to the foreground and retry."
    }
}
