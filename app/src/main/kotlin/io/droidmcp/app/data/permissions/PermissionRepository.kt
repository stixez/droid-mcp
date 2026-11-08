package io.droidmcp.app.data.permissions

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which capabilities are granted right now, keyed by [Capability.id]. */
interface PermissionSource {
    val granted: StateFlow<Set<String>>

    /** Re-check every capability. Cheap; call it whenever the user may have changed something. */
    fun refresh()
}

/**
 * Android has no callback for most of these (special access is toggled in Settings), so the app
 * re-checks on every resume and after each grant flow returns.
 */
class PermissionRepository(private val context: Context) : PermissionSource {

    private val _granted = MutableStateFlow(check())
    override val granted: StateFlow<Set<String>> = _granted.asStateFlow()

    override fun refresh() {
        _granted.value = check()
    }

    private fun check(): Set<String> = Capabilities.all
        .filter { capability ->
            try {
                capability.isGranted(context)
            } catch (e: Exception) {
                // A vendor ROM throwing from a Settings query must not take the app down.
                Log.w(TAG, "Couldn't check ${capability.id}", e)
                false
            }
        }
        .map { it.id }
        .toSet()

    private companion object {
        const val TAG = "PermissionRepository"
    }
}
