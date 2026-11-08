package io.droidmcp.app.feature.computers

import androidx.lifecycle.viewModelScope
import io.droidmcp.app.core.mvi.MviViewModel
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.PairingControl
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.app.data.settings.SettingsStore
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class ComputersViewModel(
    private val pairing: PairingControl,
    private val settings: SettingsStore,
    network: Flow<LocalNetwork>,
    recentCalls: Flow<List<ToolCallAudit>>,
    status: Flow<ServerStatus> = flowOf(ServerStatus.Stopped),
) : MviViewModel<ComputersState, ComputersIntent, ComputersEffect>(ComputersState()) {

    init {
        viewModelScope.launch {
            combine(settings.settings, recentCalls, status) { s, calls, st -> Triple(s, calls, st) }.collect { (s, calls, st) ->
                val lastUse = calls.groupBy { it.clientLabel }.mapValues { (_, rows) -> rows.maxOf { it.timestamp } }
                // A running server keeps its port and scheme until restarted, whatever Settings says now.
                val live = st as? ServerStatus.Running
                setState {
                    copy(
                        computers = s.pairedComputers.map { ComputerUi(it.name, it.createdAt, lastUse[it.name]) },
                        port = live?.port ?: s.activePort,
                        tls = live?.tls ?: s.tlsEnabled,
                        // Keep the open sheet in sync if its computer was revoked elsewhere.
                        showing = showing?.takeIf { open -> s.pairedComputers.any { it.name == open.name } },
                    )
                }
            }
        }
        viewModelScope.launch {
            network.collect { n -> setState { copy(host = n.address) } }
        }
    }

    override fun onIntent(intent: ComputersIntent) {
        when (intent) {
            ComputersIntent.AskAdd -> setState { copy(nameInput = "", nameError = null) }
            is ComputersIntent.EditName -> setState { copy(nameInput = intent.text.take(60), nameError = null) }
            ComputersIntent.CancelAdd -> setState { copy(nameInput = null, nameError = null) }
            ComputersIntent.ConfirmAdd -> {
                val name = currentState.nameInput ?: return
                val error = validateComputerName(name, currentState.computers.map { it.name })
                if (error != null) {
                    setState { copy(nameError = error) }
                    return
                }
                viewModelScope.launch {
                    val created = pairing.addComputer(name.trim())
                    setState { copy(nameInput = null, showing = created) }
                }
            }
            is ComputersIntent.Show -> viewModelScope.launch {
                val computer = settings.snapshot().pairedComputers.firstOrNull { it.name == intent.name } ?: return@launch
                setState { copy(showing = computer) }
            }
            ComputersIntent.HideCommand -> setState { copy(showing = null) }
            ComputersIntent.CopyCommand -> currentState.showingConnection?.let {
                emit(ComputersEffect.Copy(it.claudeCodeCommand(), "Command"))
            }
            ComputersIntent.CopyJson -> currentState.showingConnection?.let {
                emit(ComputersEffect.Copy(it.jsonConfig(), "Config"))
            }
            is ComputersIntent.AskRevoke -> setState { copy(confirmRevoke = intent.name) }
            ComputersIntent.CancelRevoke -> setState { copy(confirmRevoke = null) }
            ComputersIntent.ConfirmRevoke -> {
                val name = currentState.confirmRevoke ?: return
                setState { copy(confirmRevoke = null, showing = showing?.takeIf { it.name != name }) }
                viewModelScope.launch {
                    pairing.revokeComputer(name)
                    emit(ComputersEffect.Message("$name can no longer connect"))
                }
            }
        }
    }
}
