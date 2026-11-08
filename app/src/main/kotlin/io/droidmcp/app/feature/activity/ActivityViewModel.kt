package io.droidmcp.app.feature.activity

import androidx.lifecycle.viewModelScope
import io.droidmcp.app.core.mvi.MviViewModel
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the Activity screen needs from the audit log. */
interface ActivityLog {
    fun observe(limit: Int): Flow<List<ToolCallAudit>>
    suspend fun clear()
    suspend fun exportJson(): String
}

class ActivityViewModel(private val log: ActivityLog) :
    MviViewModel<ActivityState, ActivityIntent, ActivityEffect>(ActivityState()) {

    init {
        viewModelScope.launch {
            log.observe(limit = FEED_LIMIT).collect { rows ->
                setState { copy(loading = false, items = rows.toItems()) }
            }
        }
    }

    override fun onIntent(intent: ActivityIntent) {
        when (intent) {
            is ActivityIntent.SetFilter -> setState { copy(filter = intent.filter) }
            is ActivityIntent.Select -> setState { copy(selected = intent.item) }
            ActivityIntent.DismissDetail -> setState { copy(selected = null) }
            ActivityIntent.AskClear -> setState { copy(confirmClear = true) }
            ActivityIntent.CancelClear -> setState { copy(confirmClear = false) }
            ActivityIntent.ConfirmClear -> {
                setState { copy(confirmClear = false) }
                viewModelScope.launch {
                    log.clear()
                    emit(ActivityEffect.DeleteExports)
                    emit(ActivityEffect.Message("Activity cleared"))
                }
            }
            ActivityIntent.Export -> viewModelScope.launch {
                if (currentState.items.isEmpty()) {
                    emit(ActivityEffect.Message("Nothing to export yet"))
                } else {
                    emit(ActivityEffect.ShareJson(log.exportJson()))
                }
            }
        }
    }

    private companion object {
        const val FEED_LIMIT = 500
    }
}
