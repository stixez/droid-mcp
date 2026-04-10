// sample-app/src/main/kotlin/io/droidmcp/sample/MainViewModel.kt
package io.droidmcp.sample

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.contacts.ContactsTools
import io.droidmcp.core.DroidMcp
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult
import io.droidmcp.device.DeviceTools
import io.droidmcp.sms.SmsTools
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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state

    private var droidMcp: DroidMcp? = null

    fun initialize() {
        val tools = mutableListOf<McpTool>()

        // Device tools always available (no permissions needed)
        tools.addAll(DeviceTools.all(context))

        // Add permitted tool modules
        if (CalendarTools.hasPermissions(context)) {
            tools.addAll(CalendarTools.all(context))
        }
        if (ContactsTools.hasPermissions(context)) {
            tools.addAll(ContactsTools.all(context))
        }
        if (SmsTools.hasPermissions(context)) {
            tools.addAll(SmsTools.all(context))
        }

        droidMcp = DroidMcp.builder()
            .addTools(tools)
            .enableHttpServer(port = 8080)
            .build()

        _state.value = _state.value.copy(tools = tools)
    }

    fun callTool(name: String, params: Map<String, Any>) {
        viewModelScope.launch {
            val result = droidMcp?.callTool(name, params)
                ?: ToolResult.error("DroidMcp not initialized")
            val log = ToolCallLog(name, params, result)
            _state.value = _state.value.copy(
                logs = listOf(log) + _state.value.logs
            )
        }
    }

    fun startServer() {
        droidMcp?.startServer()
        _state.value = _state.value.copy(
            serverRunning = true,
            serverUrl = "http://<device-ip>:8080/mcp"
        )
    }

    fun stopServer() {
        droidMcp?.stopServer()
        _state.value = _state.value.copy(serverRunning = false, serverUrl = null)
    }

    override fun onCleared() {
        droidMcp?.stopServer()
    }
}
