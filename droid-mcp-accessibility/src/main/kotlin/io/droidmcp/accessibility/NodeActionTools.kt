@file:Suppress("DEPRECATION") // AccessibilityNodeInfo.recycle()

package io.droidmcp.accessibility

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * The shared selector parameter list reused by every node-action tool
 * ([ClickNodeTool], [LongClickNodeTool], [SetNodeTextTool], [ScrollNodeTool]):
 * `text`, `view_id`, `class_name`, `package_name`, `index`. See [parseSelector]
 * for the validation rules.
 */
private fun nodeSelectorParams(): List<ToolParameter> = listOf(
    ToolParameter("text", "Substring match against node text / contentDescription.", ParameterType.STRING, required = false),
    ToolParameter("view_id", "Exact match against view-id resource name.", ParameterType.STRING, required = false),
    ToolParameter("class_name", "Exact match against node class.", ParameterType.STRING, required = false),
    ToolParameter("package_name", "Exact match against node package.", ParameterType.STRING, required = false),
    ToolParameter("index", "If the selector matches multiple nodes, zero-indexed pick. Must be >= 0. Default 0.", ParameterType.INTEGER, required = false),
)

/**
 * Parsed selector for the node-action tools.
 *
 * @property text Case-insensitive substring against text + content-description.
 * @property viewId Exact view-id resource name.
 * @property className Exact node class.
 * @property packageName Exact node package.
 * @property index Zero-based pick among BFS-ordered matches (>= 0).
 */
private data class SelectorCriteria(
    val text: String?,
    val viewId: String?,
    val className: String?,
    val packageName: String?,
    val index: Int,
)

/**
 * Parse and validate the shared selector params into a [SelectorCriteria].
 *
 * @param params The raw tool params.
 * @return `success` with the criteria, or `failure` carrying an
 *   [IllegalArgumentException] whose message becomes the tool error when no
 *   selector field is supplied or `index` is negative.
 */
private fun parseSelector(params: Map<String, Any>): Result<SelectorCriteria> {
    val text = (params["text"] as? String)?.takeIf { it.isNotBlank() }
    val viewId = (params["view_id"] as? String)?.takeIf { it.isNotBlank() }
    val className = (params["class_name"] as? String)?.takeIf { it.isNotBlank() }
    val pkg = (params["package_name"] as? String)?.takeIf { it.isNotBlank() }
    if (text == null && viewId == null && className == null && pkg == null) {
        return Result.failure(IllegalArgumentException("At least one of text, view_id, class_name, or package_name is required."))
    }
    val index = (params["index"] as? Number)?.toInt() ?: 0
    if (index < 0) {
        return Result.failure(IllegalArgumentException("index must be >= 0."))
    }
    return Result.success(SelectorCriteria(text, viewId, className, pkg, index))
}

/**
 * Resolve a node from the shared selector and invoke a single
 * `AccessibilityNodeInfo.performAction` on it. Shared by all four node-action
 * tools.
 *
 * On success returns `success = true`, `action` (the [actionName] string), and
 * the matched node's `view_id`. Error cases (all long-form messages except
 * `node_not_editable`, which is short-form): service not bound
 * ([notConnectedError]), invalid selector (from [parseSelector]), no node
 * matched, `node_not_editable` when `requireEditable` and the node isn't
 * editable, and a "performAction returned false" message when the node rejects
 * the action.
 *
 * @param action The `AccessibilityNodeInfo.ACTION_*` constant to perform.
 * @param params The raw tool params (selector + any action-specific keys).
 * @param arguments Optional action arguments (e.g. set-text payload).
 * @param requireEditable When true, fails with `node_not_editable` unless the
 *   matched node reports `isEditable`.
 * @return The [ToolResult] to hand back from the calling tool's `execute`.
 */
private fun perform(
    action: Int,
    params: Map<String, Any>,
    arguments: Bundle? = null,
    requireEditable: Boolean = false,
): ToolResult {
    if (!AccessibilityServiceHolder.isConnected()) return ToolResult.error(notConnectedError())
    val criteria = parseSelector(params).getOrElse { return ToolResult.error(it.message ?: "invalid selector") }

    val result = NodeQuery.withRoot { root ->
        val node = NodeQuery.findOne(
            root = root,
            predicate = { NodeQuery.matches(it, criteria.text, criteria.viewId, criteria.className, criteria.packageName) },
            index = criteria.index,
        ) ?: return@withRoot ToolResult.error("No node matched the selector.")
        try {
            if (requireEditable && !node.isEditable) {
                return@withRoot ToolResult.error("node_not_editable", null)
            }
            val ok = if (arguments != null) node.performAction(action, arguments) else node.performAction(action)
            if (ok) {
                ToolResult.success(mapOf(
                    "success" to true,
                    "action" to actionName(action),
                    "view_id" to node.viewIdResourceName,
                ))
            } else {
                ToolResult.error("performAction returned false — node may not support this action.")
            }
        } finally {
            node.recycle()
        }
    }
    return result ?: ToolResult.error(notConnectedError())
}

/** Map an `AccessibilityNodeInfo.ACTION_*` constant to the short string echoed
 *  back in the `action` result key. */
private fun actionName(action: Int): String = when (action) {
    AccessibilityNodeInfo.ACTION_CLICK -> "click"
    AccessibilityNodeInfo.ACTION_LONG_CLICK -> "long_click"
    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "scroll_forward"
    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "scroll_backward"
    AccessibilityNodeInfo.ACTION_SET_TEXT -> "set_text"
    else -> "action_$action"
}

/**
 * `click_node` — resolve a node via the shared selector and perform
 * `ACTION_CLICK` on it. See [perform] for the result/error shape; on success
 * returns `success`, `action = "click"`, and the matched `view_id`.
 */
class ClickNodeTool(private val context: Context) : McpTool {
    override val name = "click_node"
    override val description = "Perform ACTION_CLICK on a node matched by the selector."
    override val parameters = nodeSelectorParams()
    override val annotations = ToolAnnotations(destructiveHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult =
        perform(AccessibilityNodeInfo.ACTION_CLICK, params)
}

/**
 * `long_click_node` — resolve a node via the shared selector and perform
 * `ACTION_LONG_CLICK` on it. See [perform] for the result/error shape; on
 * success returns `success`, `action = "long_click"`, and the matched
 * `view_id`.
 */
class LongClickNodeTool(private val context: Context) : McpTool {
    override val name = "long_click_node"
    override val description = "Perform ACTION_LONG_CLICK on a node matched by the selector."
    override val parameters = nodeSelectorParams()
    override val annotations = ToolAnnotations(destructiveHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult =
        perform(AccessibilityNodeInfo.ACTION_LONG_CLICK, params)
}

/**
 * `set_node_text` — replace the text of an editable node via `ACTION_SET_TEXT`.
 * Works for most native EditText fields; some apps disable accessibility
 * text-set, in which case the IME tools are the fallback.
 *
 * Params: the shared selector plus required `text` (replacement string). The
 * matched node must report `isEditable` or the call fails with the short-form
 * `node_not_editable`. See [perform]; on success returns `success`,
 * `action = "set_text"`, and the matched `view_id`.
 */
class SetNodeTextTool(private val context: Context) : McpTool {
    override val name = "set_node_text"
    override val description = "Replace the text of an editable node via ACTION_SET_TEXT. Works for most native EditText fields; some apps disable accessibility text-set, in which case use the IME tools instead."
    override val parameters = nodeSelectorParams() + ToolParameter(
        "text", "Replacement text.", ParameterType.STRING, required = true,
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return perform(AccessibilityNodeInfo.ACTION_SET_TEXT, params, args, requireEditable = true)
    }
}

/**
 * `scroll_node` — scroll a scrollable node forward or backward via
 * `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD`.
 *
 * Params: the shared selector plus optional `direction` (`forward` default, or
 * `backward`/`back`). An unrecognized `direction` returns a validation error.
 * See [perform]; on success returns `success`, the `action`
 * (`scroll_forward`/`scroll_backward`), and the matched `view_id`.
 */
class ScrollNodeTool(private val context: Context) : McpTool {
    override val name = "scroll_node"
    override val description = "Scroll a scrollable node forward or backward."
    override val parameters = nodeSelectorParams() + ToolParameter(
        "direction", "'forward' (default) or 'backward'.", ParameterType.STRING, required = false,
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val action = when ((params["direction"] as? String)?.lowercase()) {
            null, "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "backward", "back" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return ToolResult.error("direction must be 'forward' or 'backward'.")
        }
        return perform(action, params)
    }
}
