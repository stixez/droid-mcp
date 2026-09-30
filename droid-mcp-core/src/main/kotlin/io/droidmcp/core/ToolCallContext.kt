package io.droidmcp.core

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * One progress update from a running tool; see [reportProgress].
 *
 * @property progress Work done so far. Must increase with each update.
 * @property total Total work, when known (e.g. bytes, files, seconds).
 * @property message Optional human-readable status.
 */
data class ProgressUpdate(
    val progress: Double,
    val total: Double? = null,
    val message: String? = null,
)

/**
 * Report progress from inside [McpTool.execute]. Over MCP this becomes a
 * `notifications/progress` message when the client asked for progress (it sent a
 * `progressToken`); in-process callers receive it through the `onProgress` overload of
 * [DroidMcp.callTool]. Otherwise it does nothing, so tools can call it unconditionally.
 */
suspend fun reportProgress(progress: Double, total: Double? = null, message: String? = null) {
    currentCoroutineContext()[ProgressSink]?.sink?.invoke(ProgressUpdate(progress, total, message))
}

/**
 * The answer to an [elicit] request.
 *
 * @property action What the user did: [Action.ACCEPT] (submitted the form), [Action.DECLINE]
 *   (explicitly said no) or [Action.CANCEL] (dismissed it).
 * @property content The submitted values, keyed by the requested schema's property names.
 *   Empty unless [action] is [Action.ACCEPT].
 */
data class ElicitationResult(
    val action: Action,
    val content: Map<String, Any?> = emptyMap(),
) {
    enum class Action { ACCEPT, DECLINE, CANCEL }
}

/**
 * Ask the MCP client's user for input or confirmation while a tool runs (MCP `elicitation/create`).
 *
 * [requestedSchema] is a flat JSON Schema object whose properties are primitives, e.g.
 * `mapOf("type" to "object", "properties" to mapOf("confirm" to mapOf("type" to "boolean")))`.
 * Per the MCP spec, never use it to ask for passwords, tokens or other secrets.
 *
 * Returns null when elicitation isn't possible for this call: in-process calls, clients that
 * didn't declare the `elicitation` capability, or clients that don't accept streamed responses.
 * Bounded by the tool timeout like the rest of the call.
 */
suspend fun elicit(message: String, requestedSchema: Map<String, Any>): ElicitationResult? =
    currentCoroutineContext()[ElicitationSink]?.request?.invoke(message, requestedSchema)

/** Installed by a transport around a tool call so [reportProgress] reaches the caller. */
internal class ProgressSink(val sink: suspend (ProgressUpdate) -> Unit) : AbstractCoroutineContextElement(ProgressSink) {
    companion object Key : CoroutineContext.Key<ProgressSink>
}

/** Installed by the MCP protocol around a tool call when the client can answer [elicit]. */
internal class ElicitationSink(
    val request: suspend (message: String, schema: Map<String, Any>) -> ElicitationResult,
) : AbstractCoroutineContextElement(ElicitationSink) {
    companion object Key : CoroutineContext.Key<ElicitationSink>
}
