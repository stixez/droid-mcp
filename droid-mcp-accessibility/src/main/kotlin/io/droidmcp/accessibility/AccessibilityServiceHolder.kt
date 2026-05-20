package io.droidmcp.accessibility

/**
 * Process-wide handle to the host app's [DroidMcpAccessibilityService] instance.
 * Set automatically when the service connects to the system, cleared on
 * disconnect / destroy. Tools call through this rather than relying on
 * `Context.getSystemService(AccessibilityService::class.java)`, which doesn't
 * give back the running service instance.
 */
object AccessibilityServiceHolder {
    @Volatile
    internal var instance: DroidMcpAccessibilityService? = null
        private set

    val service: DroidMcpAccessibilityService?
        get() = instance

    fun isConnected(): Boolean = instance != null

    internal fun set(svc: DroidMcpAccessibilityService) {
        instance = svc
    }

    internal fun clear(svc: DroidMcpAccessibilityService?) {
        if (svc == null || instance === svc) instance = null
    }
}
