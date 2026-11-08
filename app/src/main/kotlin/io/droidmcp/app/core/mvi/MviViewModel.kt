package io.droidmcp.app.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Unidirectional base for every screen:
 *
 * - the UI sends [Intent]s through [onIntent], and nothing else
 * - the ViewModel owns one immutable [State], exposed as [state]
 * - one-off events the UI must act on exactly once (open Settings, show a snackbar, launch a
 *   permission dialog) go through [effects], never through state
 *
 * State changes go through [setState] with a reducer, so every transition is a plain
 * `State -> State` function that tests can call directly.
 */
abstract class MviViewModel<State, Intent, Effect>(initial: State) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    /** Current state snapshot, for reading inside intent handlers. */
    protected val currentState: State get() = _state.value

    abstract fun onIntent(intent: Intent)

    protected fun setState(reducer: State.() -> State) = _state.update(reducer)

    protected fun emit(effect: Effect) {
        _effects.trySend(effect)
    }
}
