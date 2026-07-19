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

    /** The currently-bound service instance, or null when none is connected. */
    val service: DroidMcpAccessibilityService?
        get() = instance

    /** @return true while a [DroidMcpAccessibilityService] is bound. */
    fun isConnected(): Boolean = instance != null

    /**
     * Record [svc] as the active instance. Called by
     * [DroidMcpAccessibilityService.onServiceConnected]; not for host use.
     *
     * @param svc The newly-connected service.
     */
    internal fun set(svc: DroidMcpAccessibilityService) {
        instance = svc
    }

    /**
     * Clear the held instance on disconnect/destroy. The identity guard ensures
     * a stale teardown can't null out a newer instance that has since rebound.
     *
     * @param svc The service being torn down, or null to clear unconditionally.
     */
    internal fun clear(svc: DroidMcpAccessibilityService?) {
        if (svc == null || instance === svc) instance = null
    }
}
