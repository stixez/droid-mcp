package io.droidmcp.ime

/**
 * Process-wide handle to the host app's [DroidMcpInputMethodService] instance.
 * Set automatically while the IME is active (visible / has an input connection
 * bound), cleared otherwise.
 */
object InputMethodServiceHolder {
    @Volatile
    internal var instance: DroidMcpInputMethodService? = null
        private set

    val service: DroidMcpInputMethodService?
        get() = instance

    fun isActive(): Boolean = instance?.hasInputConnection() == true

    internal fun set(svc: DroidMcpInputMethodService) {
        instance = svc
    }

    internal fun clear(svc: DroidMcpInputMethodService?) {
        if (svc == null || instance === svc) instance = null
    }
}
