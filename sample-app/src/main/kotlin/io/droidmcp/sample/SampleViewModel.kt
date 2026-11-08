package io.droidmcp.sample

import android.app.Application
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.core.DroidMcp
import io.droidmcp.device.DeviceTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address

data class SampleState(
    val toolCount: Int = 0,
    val calendarGranted: Boolean = false,
    val lastResult: String? = null,
    val serverRunning: Boolean = false,
    val connectCommand: String? = null,
)

/**
 * One [DroidMcp] used two ways: called directly, as an on-device model would, and served over
 * HTTP to MCP clients on your computer.
 *
 * The server lives in this ViewModel, so it stops when you leave the screen. To keep it running
 * in the background, host it in a foreground service (`droid-mcp-server-service`); the `:app`
 * module in this repo does that.
 */
class SampleViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Application get() = getApplication()

    private val mcp: DroidMcp = DroidMcp.builder()
        .addTools(DeviceTools.all(app))
        .enableHttpServer(port = PORT, context = app) // token generated
        .build()

    private val _state = MutableStateFlow(SampleState())
    val state: StateFlow<SampleState> = _state.asStateFlow()

    init {
        refreshTools()
    }

    /**
     * Adds the calendar tools once the user has granted calendar access. The library never asks
     * for permissions itself; the app does, then registers the tools. Connected clients are told
     * the tool list changed.
     */
    fun refreshTools() {
        val granted = CalendarTools.hasPermissions(context)
        if (granted) mcp.registerTools(CalendarTools.all(context))
        _state.update { it.copy(toolCount = mcp.listTools().size, calendarGranted = granted) }
    }

    /** What an app with an on-device model does with a tool call the model produced. */
    fun call(name: String, arguments: Map<String, Any> = emptyMap()) {
        viewModelScope.launch {
            val result = mcp.callTool(name, arguments)
            val text = if (result.isSuccess) result.data.toString() else "Error: ${result.errorMessage}"
            _state.update { it.copy(lastResult = "$name\n$text") }
        }
    }

    fun toggleServer() {
        viewModelScope.launch {
            val running = withContext(Dispatchers.IO) {
                if (mcp.isServerRunning()) mcp.stopServer() else mcp.startServer()
                mcp.isServerRunning()
            }
            _state.update { it.copy(serverRunning = running, connectCommand = if (running) connectCommand() else null) }
        }
    }

    override fun onCleared() {
        mcp.stopServer()
    }

    private fun connectCommand(): String {
        val host = wifiAddress() ?: "PHONE_IP"
        return "claude mcp add --transport http phone http://$host:$PORT/mcp " +
            "--header \"Authorization: Bearer ${mcp.serverToken}\""
    }

    /** The phone's IPv4 address on its current network, which is what the computer connects to. */
    private fun wifiAddress(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val link = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return link.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    private companion object {
        const val PORT = 8080
    }
}
