// sample-app/src/main/kotlin/io/droidmcp/sample/MainViewModel.kt
package io.droidmcp.sample

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.Build
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.Inet4Address
import io.droidmcp.qr.GenerateQrCodeTool
import io.droidmcp.alarms.AlarmsTools
import io.droidmcp.apps.AppsTools
import io.droidmcp.bluetooth.BluetoothTools
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.calllog.CallLogTools
import io.droidmcp.clipboard.ClipboardTools
import io.droidmcp.contacts.ContactsTools
import io.droidmcp.audit.RoomAuditSink
import io.droidmcp.core.DroidMcp
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolResult
import io.droidmcp.core.transport.TlsConfig
import io.droidmcp.server.DroidMcpServerService
import io.droidmcp.tls.SelfSignedCert
import java.io.File
import io.droidmcp.device.DeviceTools
import io.droidmcp.downloads.DownloadsTools
import io.droidmcp.files.FilesTools
import io.droidmcp.health.HealthTools
import io.droidmcp.location.LocationTools
import io.droidmcp.media.MediaTools
import io.droidmcp.network.NetworkTools
import io.droidmcp.notifications.NotificationsTools
import io.droidmcp.sensors.SensorTools
import io.droidmcp.qr.QrTools
import io.droidmcp.camera.CameraTools
import io.droidmcp.audio.AudioTools
import io.droidmcp.telephony.TelephonyTools
import io.droidmcp.vibration.VibrationTools
import io.droidmcp.flashlight.FlashlightTools
import io.droidmcp.biometric.BiometricTools
import io.droidmcp.screen.ScreenTools
import io.droidmcp.settings.SettingsTools
import io.droidmcp.sms.SmsTools
import io.droidmcp.tts.TtsTools
import io.droidmcp.web.WebTools
import io.droidmcp.wifi.WifiTools
import io.droidmcp.nfc.NfcTools
import io.droidmcp.intent.IntentTools
import io.droidmcp.accessibility.AccessibilityTools
import io.droidmcp.ime.ImeTools
import io.droidmcp.notificationsreply.NotificationsReplyTools
import io.droidmcp.notificationwatch.NotificationWatchTools
import io.droidmcp.overlay.OverlayTools
import io.droidmcp.playback.PlaybackTools
import io.droidmcp.root.RootTools
import io.droidmcp.shizuku.ShizukuTools
import io.droidmcp.screenshot.ScreenshotTools
import io.droidmcp.dnd.DndTools
import io.droidmcp.keyguard.KeyguardTools
import io.droidmcp.wallpaper.WallpaperTools
import io.droidmcp.ringtone.RingtoneTools
import io.droidmcp.usb.UsbTools
import io.droidmcp.print.PrintTools
import io.droidmcp.mlkit.MlKitTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToolCallLog(
    val toolName: String,
    val params: Map<String, Any>,
    val result: ToolResult,
    val timestamp: Long = System.currentTimeMillis(),
)

data class MainState(
    val tools: List<McpTool> = emptyList(),
    val serverRunning: Boolean = false,
    val serverUrl: String? = null,
    val serverToken: String? = null,
    val pairingQr: Bitmap? = null,
    val logs: List<ToolCallLog> = emptyList(),
    val loading: Boolean = false,
    val readOnly: Boolean = false,
    /** Tool names the host has gated off — hidden from tools/list, rejected by tools/call. */
    val disabledTools: Set<String> = emptySet(),
    /** Persisted HTTP tools/call audit trail, newest first (0.10.0 hardening). */
    val auditLog: List<ToolCallAudit> = emptyList(),
    /** Whether the HTTP server terminates TLS (binds the HTTPS port, https scheme). */
    val tlsEnabled: Boolean = false,
    /** SHA-256 fingerprint of the self-signed cert to pin client-side; null when plaintext. */
    val tlsFingerprint: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state

    private var droidMcp: DroidMcp? = null

    /** Persists every HTTP tools/call to a private Room DB (0.10.0 hardening). */
    private val auditSink = RoomAuditSink(context)

    init {
        // Stream the persisted audit trail into state for the Audit tab. Room's
        // Flow re-emits on every insert/prune, so the UI stays live without
        // polling. Started once; survives initialize() re-runs.
        viewModelScope.launch {
            auditSink.observe(limit = 200)
                .catch { /* DB closed during teardown — ignore */ }
                .collect { entries ->
                    _state.value = _state.value.copy(auditLog = entries)
                }
        }
    }

    fun initialize() {
        val tools = mutableListOf<McpTool>()

        // Always available (no permissions needed)
        tools.addAll(DeviceTools.all(context))
        tools.addAll(ClipboardTools.all(context))
        tools.addAll(AppsTools.all(context))
        tools.addAll(ScreenTools.all(context))
        tools.addAll(TtsTools.all(context))
        tools.addAll(NotificationsTools.all(context))

        // Permission-gated modules
        if (CalendarTools.hasPermissions(context)) tools.addAll(CalendarTools.all(context))
        if (ContactsTools.hasPermissions(context)) tools.addAll(ContactsTools.all(context))
        if (SmsTools.hasPermissions(context)) tools.addAll(SmsTools.all(context))
        if (FilesTools.hasPermissions(context)) tools.addAll(FilesTools.all(context))
        if (CallLogTools.hasPermissions(context)) tools.addAll(CallLogTools.all(context))
        if (MediaTools.hasPermissions(context)) tools.addAll(MediaTools.all(context))
        if (LocationTools.hasPermissions(context)) tools.addAll(LocationTools.all(context))
        if (HealthTools.hasPermissions(context)) tools.addAll(HealthTools.all(context))
        if (AlarmsTools.hasPermissions(context)) tools.addAll(AlarmsTools.all(context))
        if (SettingsTools.hasPermissions(context)) tools.addAll(SettingsTools.all(context))
        if (BluetoothTools.hasPermissions(context)) tools.addAll(BluetoothTools.all(context))
        if (WifiTools.hasPermissions(context)) tools.addAll(WifiTools.all(context))
        if (DownloadsTools.hasPermissions(context)) tools.addAll(DownloadsTools.all(context))
        tools.addAll(NetworkTools.all(context)) // PACKAGE_USAGE_STATS is a special permission; tools handle missing access
        if (TelephonyTools.hasPermissions(context)) tools.addAll(TelephonyTools.all(context))
        if (VibrationTools.hasPermissions(context)) tools.addAll(VibrationTools.all(context))
        if (FlashlightTools.hasPermissions(context)) tools.addAll(FlashlightTools.all(context))
        if (QrTools.hasPermissions(context)) tools.addAll(QrTools.all(context))
        if (CameraTools.hasPermissions(context)) tools.addAll(CameraTools.all(context))

        // Always available (no permissions)
        tools.addAll(BiometricTools.all(context))
        tools.addAll(SensorTools.all(context))
        tools.addAll(AudioTools.all(context))
        tools.addAll(WebTools.all(context))

        // New modules — always available
        // NFC, DND, Wallpaper use install-time (normal) permissions, auto-granted at install.
        // Their tools handle special permissions (notification policy, WRITE_SETTINGS) gracefully at execution time.
        tools.addAll(NfcTools.all(context))
        tools.addAll(IntentTools.all(context))
        tools.addAll(PlaybackTools.all(context))
        tools.addAll(NotificationsReplyTools.all(context))
        tools.addAll(NotificationWatchTools.all(context))
        tools.addAll(AccessibilityTools.all(context))
        tools.addAll(ImeTools.all(context))
        tools.addAll(ShizukuTools.all(context))
        // Root tools share the same names as Shizuku tools and would overwrite.
        // The sample app demonstrates registering both so users can see the
        // two permission flows; in practice, a host should pick one OR write
        // a fallback wrapper.
        if (RootTools.isRootAvailable()) {
            // Last write wins — when root is granted, route the shell tools
            // through libsu instead of Shizuku.
            tools.addAll(RootTools.all(context))
        }
        // OverlayTools.all is intentionally empty — overlay is host-API only.
        tools.addAll(ScreenshotTools.all(context))
        tools.addAll(DndTools.all(context))
        tools.addAll(KeyguardTools.all(context))
        tools.addAll(WallpaperTools.all(context))
        tools.addAll(RingtoneTools.all(context))
        tools.addAll(UsbTools.all(context))
        tools.addAll(PrintTools.all(context))
        tools.addAll(MlKitTools.all(context))

        droidMcp = newServer(tools)

        // A rebuild (or re-init on a permission grant) starts from a fresh
        // registry, so re-apply any gating the user had set — pruned to the
        // tools that actually exist now. A tool that dropped out of the
        // registry (e.g. permission revoked, root no longer available) must not
        // linger in the disabled set, or it would skew the UI counts/badge.
        val gated = _state.value.disabledTools intersect tools.map { it.name }.toSet()
        droidMcp?.setDisabledTools(gated)

        _state.value = _state.value.copy(
            tools = tools,
            disabledTools = gated,
            tlsFingerprint = droidMcp?.tlsFingerprint,
        )
    }

    fun callTool(name: String, params: Map<String, Any>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            val result = droidMcp?.callTool(name, params)
                ?: ToolResult.error("DroidMcp not initialized")
            val log = ToolCallLog(name, params, result)
            _state.value = _state.value.copy(
                logs = listOf(log) + _state.value.logs,
                loading = false,
            )
        }
    }

    fun startServer() {
        // Run the server inside a foreground service so it survives screen-off /
        // backgrounding. The service picks up droidMcp via McpServerHolder and
        // calls startServer() on it; we set serverRunning optimistically (the
        // service starts immediately after startForegroundService).
        McpServerHolder.server = droidMcp
        DroidMcpServerService.start(context, McpServerService::class.java)
        val tls = _state.value.tlsEnabled
        val scheme = if (tls) "https" else "http"
        val port = if (tls) HTTPS_PORT else HTTP_PORT
        val url = "$scheme://${getDeviceIp()}:$port/mcp"
        val token = droidMcp?.serverToken
        val fingerprint = droidMcp?.tlsFingerprint
        _state.value = _state.value.copy(
            serverRunning = true,
            serverUrl = url,
            serverToken = token,
            tlsFingerprint = fingerprint,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val qr = generatePairingQr(url, token, fingerprint)
            _state.value = _state.value.copy(pairingQr = qr)
        }
    }

    fun stopServer() {
        // Tears down the foreground service, whose onDestroy stops the HTTP
        // listener on the shared DroidMcp.
        DroidMcpServerService.stop(context, McpServerService::class.java)
        _state.value = _state.value.copy(
            serverRunning = false,
            serverUrl = null,
            serverToken = null,
            pairingQr = null,
        )
    }

    fun setReadOnly(value: Boolean) {
        if (_state.value.serverRunning) return // toggle disabled while running
        _state.value = _state.value.copy(readOnly = value)
        rebuildDroidMcp()
    }

    /**
     * Toggle TLS on the HTTP transport. Like [setReadOnly], it rebuilds the
     * server, so it's only allowed while stopped. Generating the self-signed
     * cert on first enable can touch disk, so it runs off the main thread; the
     * fingerprint lands in state when the rebuild completes.
     */
    fun setTls(value: Boolean) {
        if (_state.value.serverRunning) return
        viewModelScope.launch {
            // Keep ONLY the blocking cert work (RSA keygen + keystore I/O on the
            // first enable) off the main thread. The state mutation + server
            // rebuild then run on the main dispatcher — same as setReadOnly — so
            // they can't interleave with initialize()/setReadOnly mutating _state
            // and reassigning droidMcp from the main thread (permission grants
            // call initialize() regardless of server state).
            if (value) withContext(Dispatchers.IO) { loadTlsConfig() }
            _state.value = _state.value.copy(tlsEnabled = value)
            rebuildDroidMcp()
            _state.value = _state.value.copy(tlsFingerprint = droidMcp?.tlsFingerprint)
        }
    }

    /**
     * Gate a single tool on or off at runtime. Takes effect immediately on the
     * live registry — no rebuild, even while the server is running.
     */
    fun setToolEnabled(name: String, enabled: Boolean) {
        droidMcp?.setToolEnabled(name, enabled)
        _state.value = _state.value.copy(
            disabledTools = droidMcp?.disabledTools() ?: _state.value.disabledTools,
        )
    }

    /**
     * Enable or disable a batch of tools in one swap — the grid's bulk actions,
     * scoped to whatever the user currently has filtered into view (so "Disable
     * all" while a filter is active only touches the visible tools). [names]
     * always come from the live registry, so this can't introduce stale entries.
     */
    fun setToolsEnabled(names: Set<String>, enabled: Boolean) {
        val updated = if (enabled) _state.value.disabledTools - names
        else _state.value.disabledTools + names
        droidMcp?.setDisabledTools(updated)
        _state.value = _state.value.copy(
            disabledTools = droidMcp?.disabledTools() ?: updated,
        )
    }

    private fun rebuildDroidMcp() {
        droidMcp = newServer(_state.value.tools)
        // Preserve gating across the rebuild that a read-only / TLS toggle triggers.
        droidMcp?.setDisabledTools(_state.value.disabledTools)
    }

    /**
     * Build a [DroidMcp] for the current [tools] honoring the read-only and TLS
     * toggles. The single place the server is assembled — both [initialize] and
     * [rebuildDroidMcp] route through here.
     */
    private fun newServer(tools: List<McpTool>): DroidMcp {
        val builder = DroidMcp.builder()
            .addTools(tools)
            .enableHttpServer(port = HTTP_PORT, readOnly = _state.value.readOnly, context = context)
            .withAuditSink(auditSink)
        if (_state.value.tlsEnabled) builder.enableTls(loadTlsConfig())
        // Publish to the holder so the foreground service runs THIS instance —
        // the same one the UI drives for in-process calls and live gating.
        return builder.build().also { McpServerHolder.server = it }
    }

    /**
     * Load (or generate + persist on first use) the self-signed cert from the
     * opt-in droid-mcp-tls module. Persisting keeps the pinned fingerprint
     * stable across restarts. Cached after the first load so a later rebuild
     * (e.g. a permission-grant re-init on the main thread) reuses it instead of
     * re-reading the keystore off disk.
     */
    private var cachedTlsConfig: TlsConfig? = null

    private fun loadTlsConfig(): TlsConfig =
        cachedTlsConfig ?: SelfSignedCert
            .loadOrCreate(File(context.filesDir, "droid-mcp-tls.p12"), httpsPort = HTTPS_PORT)
            .also { cachedTlsConfig = it }

    private suspend fun generatePairingQr(url: String, token: String?, tlsFingerprint: String?): Bitmap? {
        val payload = buildJsonObject {
            put("v", 1)
            put("url", url)
            if (token != null) put("token", token)
            // Self-signed cert: the client pins this instead of validating a chain.
            if (tlsFingerprint != null) put("tls_fingerprint", tlsFingerprint)
            put("name", Build.MODEL)
        }
        val result = GenerateQrCodeTool().execute(mapOf("text" to payload.toString(), "size" to 600))
        val base64 = result.data?.get("qr_image") as? String ?: return null
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private companion object {
        const val HTTP_PORT = 8080
        const val HTTPS_PORT = 8443
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    /** Wipe the persisted audit trail. The observe() stream refreshes the UI. */
    fun clearAuditLog() {
        viewModelScope.launch { auditSink.clear() }
    }

    /**
     * Serialize the full audit history to JSON off the main thread, then hand it
     * back on the main thread (e.g. for the UI to copy to the clipboard).
     */
    fun exportAuditLog(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = auditSink.exportJson()
            onReady(json)
        }
    }

    override fun onCleared() {
        // Don't let the server outlive the UI: stop the service before closing
        // the audit sink it writes through (otherwise a late HTTP call could try
        // to record into a closing DB — harmless thanks to RoomAuditSink's own
        // guard, but cleaner to stop first).
        DroidMcpServerService.stop(context, McpServerService::class.java)
        // viewModelScope (and the audit observe() collector) is already being
        // cancelled as part of teardown by the time onCleared runs. The Flow's
        // .catch handles errors routed through the Flow, but db.close() can also
        // throw synchronously while an observer is still draining — guard it so a
        // teardown-time DB exception can't crash the process.
        try {
            auditSink.close()
        } catch (_: Exception) {
        }
        super.onCleared()
    }

    private fun getDeviceIp(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "localhost"
        val network = cm.activeNetwork ?: return "localhost"
        val linkProperties = cm.getLinkProperties(network) ?: return "localhost"
        return linkProperties.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isAnyLocalAddress }
            ?.hostAddress
            ?: "localhost"
    }
}
