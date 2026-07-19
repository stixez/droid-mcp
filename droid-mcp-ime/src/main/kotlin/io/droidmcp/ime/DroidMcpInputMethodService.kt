package io.droidmcp.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button

/**
 * Abstract InputMethodService that hosts droid-mcp's "LLM keyboard." Renders a
 * minimal input view (typing is driven by MCP tools, not by user touches on a
 * key grid) with a single "Switch back" button so users can return to their
 * normal keyboard, and registers itself with [InputMethodServiceHolder] so
 * tools can reach the currently-bound [InputConnection].
 *
 * The host app:
 *   1. Declares a concrete subclass in its manifest with
 *      `android.permission.BIND_INPUT_METHOD` and an `<intent-filter>` for
 *      `android.view.InputMethod`.
 *   2. Points `<meta-data android:name="android.view.im"
 *      android:resource="@xml/droid_mcp_ime_config" />` at the shipped default
 *      config (or supplies its own).
 *   3. User adds the keyboard via Settings > System > Languages & input >
 *      On-screen keyboard > Manage on-screen keyboards, then picks it via the
 *      IME picker.
 */
abstract class DroidMcpInputMethodService : InputMethodService() {

    @Volatile
    private var inputBound: Boolean = false

    override fun onCreate() {
        super.onCreate()
        InputMethodServiceHolder.set(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.droid_mcp_ime_view, null)
        view.findViewById<Button>(R.id.droid_mcp_ime_switch_back)?.setOnClickListener {
            runCatching { switchToPreviousInputMethod() }
        }
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputBound = true
    }

    override fun onFinishInput() {
        inputBound = false
        super.onFinishInput()
    }

    override fun onDestroy() {
        inputBound = false
        InputMethodServiceHolder.clear(this)
        super.onDestroy()
    }

    /**
     * Whether an editor field is currently bound to this IME. Tools use this
     * via [InputMethodServiceHolder.isActive] to short-circuit with a clear
     * error before trying to drive an empty InputConnection.
     */
    fun hasInputConnection(): Boolean = inputBound && currentInputConnection != null

    /**
     * Live InputConnection for the currently-focused field, or null if the
     * IME isn't currently driving an editor.
     */
    fun connection(): InputConnection? = if (inputBound) currentInputConnection else null
}
