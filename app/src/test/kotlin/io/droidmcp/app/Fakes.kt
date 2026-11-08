package io.droidmcp.app

import io.droidmcp.app.data.permissions.PermissionSource
import io.droidmcp.app.data.server.ServerControl
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun snapshot(): AppSettings = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
    override suspend fun ensureToken(): String {
        state.value.token?.let { return it }
        val token = "token-0123456789abcdef"
        state.update { it.copy(token = token) }
        return token
    }
    override suspend fun rotateToken(): String {
        val token = "rotated-0123456789abcdef"
        state.update { it.copy(token = token) }
        return token
    }
}

class FakeServer : ServerControl {
    override val status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    override val restartNeeded = MutableStateFlow(false)
    override val tlsFingerprint: StateFlow<String?> = MutableStateFlow(null)
    override val pausedWhileClosed = MutableStateFlow(false)
    val calls = mutableListOf<String>()
    val toggled = mutableListOf<Pair<Set<String>, Boolean>>()

    override fun start() {
        calls += "start"
        status.value = ServerStatus.Starting
    }

    override fun stop() {
        calls += "stop"
        status.value = ServerStatus.Stopped
    }

    override fun restart() {
        calls += "restart"
    }

    override fun restartIfRunning() {
        calls += "restartIfRunning"
    }

    override fun setToolsEnabled(names: Set<String>, enabled: Boolean) {
        toggled += names to enabled
    }

    override fun dismissPausedWhileClosed() {
        pausedWhileClosed.value = false
    }
}

class FakePermissions(initial: Set<String> = emptySet()) : PermissionSource {
    override val granted = MutableStateFlow(initial)
    var refreshes = 0
    override fun refresh() {
        refreshes++
    }
}
