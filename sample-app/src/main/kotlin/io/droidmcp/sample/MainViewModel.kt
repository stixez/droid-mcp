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
import io.droidmcp.core.ToolResult
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
import kotlinx.coroutines.launch

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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state

    private var droidMcp: DroidMcp? = null

    /** Persists every HTTP tools/call to a private Room DB (0.10.0 hardening). */
    private val auditSink = RoomAuditSink(context)

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

        droidMcp = DroidMcp.builder()
            .addTools(tools)
            .enableHttpServer(port = 8080, readOnly = _state.value.readOnly, context = context)
            .withAuditSink(auditSink)
            .build()

        _state.value = _state.value.copy(tools = tools)
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
        droidMcp?.startServer()
        val url = "http://${getDeviceIp()}:8080/mcp"
        val token = droidMcp?.serverToken
        _state.value = _state.value.copy(
            serverRunning = true,
            serverUrl = url,
            serverToken = token,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val qr = generatePairingQr(url, token)
            _state.value = _state.value.copy(pairingQr = qr)
        }
    }

    fun stopServer() {
        droidMcp?.stopServer()
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

    private fun rebuildDroidMcp() {
        val tools = _state.value.tools
        droidMcp = DroidMcp.builder()
            .addTools(tools)
            .enableHttpServer(port = 8080, readOnly = _state.value.readOnly, context = context)
            .withAuditSink(auditSink)
            .build()
    }

    private suspend fun generatePairingQr(url: String, token: String?): Bitmap? {
        val payload = buildJsonObject {
            put("v", 1)
            put("url", url)
            if (token != null) put("token", token)
            put("name", Build.MODEL)
        }
        val result = GenerateQrCodeTool().execute(mapOf("text" to payload.toString(), "size" to 600))
        val base64 = result.data?.get("qr_image") as? String ?: return null
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    override fun onCleared() {
        droidMcp?.stopServer()
        auditSink.close()
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
