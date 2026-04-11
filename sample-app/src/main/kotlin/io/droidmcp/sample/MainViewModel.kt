// sample-app/src/main/kotlin/io/droidmcp/sample/MainViewModel.kt
package io.droidmcp.sample

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.droidmcp.alarms.AlarmsTools
import io.droidmcp.apps.AppsTools
import io.droidmcp.bluetooth.BluetoothTools
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.calllog.CallLogTools
import io.droidmcp.clipboard.ClipboardTools
import io.droidmcp.contacts.ContactsTools
import io.droidmcp.core.DroidMcp
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult
import io.droidmcp.device.DeviceTools
import io.droidmcp.downloads.DownloadsTools
import io.droidmcp.files.FilesTools
import io.droidmcp.health.HealthTools
import io.droidmcp.location.LocationTools
import io.droidmcp.media.MediaTools
import io.droidmcp.notifications.NotificationsTools
import io.droidmcp.screen.ScreenTools
import io.droidmcp.settings.SettingsTools
import io.droidmcp.sms.SmsTools
import io.droidmcp.tts.TtsTools
import io.droidmcp.wifi.WifiTools
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
    val logs: List<ToolCallLog> = emptyList(),
    val loading: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state

    private var droidMcp: DroidMcp? = null

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

        droidMcp = DroidMcp.builder()
            .addTools(tools)
            .enableHttpServer(port = 8080)
            .build()

        _state.value = _state.value.copy(tools = tools)
    }

    fun callTool(name: String, params: Map<String, Any>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
        _state.value = _state.value.copy(
            serverRunning = true,
            serverUrl = "http://${getDeviceIp()}:8080/mcp"
        )
    }

    fun stopServer() {
        droidMcp?.stopServer()
        _state.value = _state.value.copy(serverRunning = false, serverUrl = null)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    override fun onCleared() {
        droidMcp?.stopServer()
    }

    private fun getDeviceIp(): String {
        val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        return if (ip != 0) {
            "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
        } else "localhost"
    }
}
