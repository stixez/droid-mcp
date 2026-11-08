package io.droidmcp.app.confirm

import io.droidmcp.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long an Allow lasts. */
enum class ApprovalScope { Once, OneHour, Always }

/** What Settings needs: see and undo remembered approvals. */
interface ApprovalMemory {
    /** Tool name -> time its 1-hour approval ends. */
    val temporary: StateFlow<Map<String, Long>>
    fun forgetAlways(tool: String)
    fun forgetTemporary()
}

/**
 * Remembers approvals beyond a single call, so a client doing ten timers in a row doesn't need
 * ten taps. "Always" is saved with the settings; "for 1 hour" lives in memory only, so a restart
 * asks again (the safe direction).
 */
class ApprovalPolicy(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ApprovalMemory {
    private val lock = Any()

    /** "Always" as last saved, plus changes made here that the saved copy doesn't show yet. */
    private var saved: Set<String> = emptySet()
    private val added = mutableSetOf<String>()
    private val removed = mutableSetOf<String>()

    private val _temporary = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Tool name -> time its 1-hour approval ends. Expired entries are dropped when read. */
    override val temporary: StateFlow<Map<String, Long>> = _temporary.asStateFlow()

    init {
        scope.launch {
            settings.settings.collect { s ->
                synchronized(lock) {
                    saved = s.alwaysAllowedTools
                    // A local change counts until the saved copy catches up, so an older emission
                    // arriving late can't undo a "Forget" or an "Always allow" just made.
                    added.retainAll { it !in saved }
                    removed.retainAll { it in saved }
                }
            }
        }
    }

    /** Runs without asking? Called on the tool-call path, so it never suspends. */
    fun isPreApproved(tool: String): Boolean {
        val always = synchronized(lock) { tool in added || (tool in saved && tool !in removed) }
        if (always) return true
        val until = _temporary.value[tool] ?: return false
        if (until > now()) return true
        _temporary.update { it - tool }
        return false
    }

    fun remember(tool: String, scope: ApprovalScope) {
        when (scope) {
            ApprovalScope.Once -> Unit
            ApprovalScope.OneHour -> _temporary.update { it + (tool to now() + ONE_HOUR_MS) }
            ApprovalScope.Always -> {
                synchronized(lock) {
                    added += tool
                    removed -= tool
                }
                this.scope.launch { settings.update { it.copy(alwaysAllowedTools = it.alwaysAllowedTools + tool) } }
            }
        }
    }

    override fun forgetAlways(tool: String) {
        synchronized(lock) {
            removed += tool
            added -= tool
        }
        scope.launch { settings.update { it.copy(alwaysAllowedTools = it.alwaysAllowedTools - tool) } }
    }

    override fun forgetTemporary() {
        _temporary.value = emptyMap()
    }

    companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000L
    }
}
