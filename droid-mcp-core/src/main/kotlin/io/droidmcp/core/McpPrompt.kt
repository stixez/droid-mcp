package io.droidmcp.core

import java.util.concurrent.ConcurrentHashMap

/** One argument a prompt accepts (MCP `prompts/list`). Values always arrive as strings. */
data class PromptArgument(
    val name: String,
    val description: String,
    val required: Boolean = false,
)

/** One message of a rendered prompt. droid-mcp prompts are text-only. */
data class PromptMessage(
    val role: Role,
    val text: String,
) {
    enum class Role(val wireName: String) { USER("user"), ASSISTANT("assistant") }
}

/** The output of [McpPrompt.render]: the messages plus an optional description of this rendering. */
data class PromptResult(
    val messages: List<PromptMessage>,
    val description: String? = null,
)

/**
 * What a completion ([McpPrompt.complete], [ResourceProvider.complete]) can draw on: the other
 * arguments the user has already filled in, and [callTool] for reading device data through a
 * read-only tool, e.g. contact names from `search_contacts`.
 */
class CompletionContext(
    val arguments: Map<String, String> = emptyMap(),
    private val toolCaller: suspend (name: String, params: Map<String, Any>) -> ToolResult? = { _, _ -> null },
) {
    /**
     * Runs the tool [name] if it is visible, read-only and needs no host confirmation; null
     * otherwise, so a suggestion never pops a confirmation dialog or changes anything.
     */
    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult? = toolCaller(name, params)
}

/**
 * A reusable prompt template a client can offer its user (MCP `prompts/list` / `prompts/get`).
 * droid-mcp ships a few in [BuiltInPrompts]; hosts can add their own via
 * [DroidMcp.Builder.addPrompt].
 *
 * @property requiredTools Tools the prompt tells the model to call. The prompt is listed only
 *   while all of them are visible, so a client never sees a prompt it can't carry out.
 */
interface McpPrompt {
    val name: String
    val title: String? get() = null
    val description: String
    val arguments: List<PromptArgument> get() = emptyList()
    val requiredTools: Set<String> get() = emptySet()

    /**
     * Build the messages for [arguments]. Required arguments are checked before this runs; throw
     * [IllegalArgumentException] for a value that is present but invalid.
     */
    suspend fun render(arguments: Map<String, String>): PromptResult

    /**
     * Suggested values for [argument], given what the user has typed so far ([value]); MCP
     * `completion/complete`. Return the best matches first; the server sends at most 100.
     * Defaults to no suggestions.
     */
    suspend fun complete(argument: String, value: String, context: CompletionContext): List<String> = emptyList()
}

/** Names of [McpPrompt.arguments] marked required but missing or blank in [arguments]. */
internal fun McpPrompt.missingArguments(arguments: Map<String, String>): List<String> =
    this.arguments.filter { it.required && arguments[it.name].isNullOrBlank() }.map { it.name }

/** Thread-safe collection of [McpPrompt]s keyed by name, with change listeners like [ToolRegistry]. */
class PromptRegistry {
    private val prompts = ConcurrentHashMap<String, McpPrompt>()
    private val listeners = ChangeListeners()

    /** Register [prompt], replacing any prompt with the same name. */
    fun register(prompt: McpPrompt) {
        prompts[prompt.name] = prompt
        notifyChanged()
    }

    fun registerAll(promptList: List<McpPrompt>) {
        if (promptList.isEmpty()) return
        promptList.forEach { prompts[it.name] = it }
        notifyChanged()
    }

    fun unregister(name: String) {
        if (prompts.remove(name) != null) notifyChanged()
    }

    fun getPrompt(name: String): McpPrompt? = prompts[name]

    fun listPrompts(): List<McpPrompt> = prompts.values.sortedBy { it.name }

    /** Prompts whose [McpPrompt.requiredTools] are all in [visibleTools]. */
    fun visiblePrompts(visibleTools: Set<String>): List<McpPrompt> =
        listPrompts().filter { visibleTools.containsAll(it.requiredTools) }

    fun addChangeListener(listener: () -> Unit) = listeners.add(listener)

    fun removeChangeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun notifyChanged() = listeners.notifyChanged()
}
