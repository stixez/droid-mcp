package io.droidmcp.ime

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus

object ImeTools {

    fun all(context: Context): List<McpTool> = listOf(
        IsImeActiveTool(context),
        TypeTextTool(context),
        CommitKeystrokeTool(context),
        DeleteTextTool(context),
        SetSelectionTool(context),
        GetTextAroundCursorTool(context),
        SwitchToPreviousImeTool(context),
    )

    /**
     * IME activation is a special access flow (Settings + IME picker), not a
     * runtime permission, so there are no manifest entries to request.
     */
    fun requiredPermissions(): List<String> = emptyList()

    /**
     * Convention method matching other modules; always returns `true` because
     * the runtime-permission concept doesn't apply here. To know whether the
     * droid-mcp keyboard is the active IME and has an editor focused, call
     * [isImeBound] (or [permissionStatus]) instead.
     */
    fun hasPermissions(context: Context): Boolean = true

    /**
     * True when the host app's [DroidMcpInputMethodService] is the currently
     * active IME *and* an editor is bound to it.
     */
    fun isImeBound(): Boolean = InputMethodServiceHolder.isActive()

    /**
     * Special-access status. Returns `Granted` when the IME is the active
     * keyboard with a bound editor, otherwise `NotGranted` carrying the IME
     * settings intent so the host can render an inline Grant card.
     *
     * Note: "granted" here means "ready to type" — the IME service is bound
     * AND an editor field has focus. If the keyboard is set as the user's
     * default but no editor is currently focused, this reports `NotGranted`
     * because no tool can succeed yet.
     */
    fun permissionStatus(context: Context): PermissionStatus =
        if (isImeBound()) {
            PermissionStatus.Granted("droid-mcp IME is active with an editor bound")
        } else {
            PermissionStatus.NotGranted(
                message = "droid-mcp IME is not the active keyboard, or no editor is focused. Open Settings to enable + select it via the IME picker.",
                intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            )
        }

    fun supportedTools(context: Context): Set<String> =
        all(context).map { it.name }.toSet()
}

internal fun imeNotActiveError(): String =
    "droid-mcp IME is not the active keyboard. The user must switch to it via the IME picker (or via accessibility's global_action) before this tool can run."
