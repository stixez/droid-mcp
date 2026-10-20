package io.droidmcp.core

import java.util.concurrent.CopyOnWriteArrayList

/** Change listeners shared by [ToolRegistry], [ResourceRegistry] and [PromptRegistry]. */
internal class ChangeListeners {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun add(listener: () -> Unit) {
        listeners += listener
    }

    fun remove(listener: () -> Unit) {
        listeners -= listener
    }

    /** Call every listener; a failing one can't affect the others or the change that triggered it. */
    fun notifyChanged() {
        listeners.forEach { runCatching { it() } }
    }
}
