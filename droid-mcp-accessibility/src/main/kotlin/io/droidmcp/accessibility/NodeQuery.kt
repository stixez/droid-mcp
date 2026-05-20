@file:Suppress("DEPRECATION") // AccessibilityNodeInfo.recycle() is a no-op on API 33+ but still needed for 28-32

package io.droidmcp.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Shared lookup + serialization helpers used by every node-targeting tool.
 *
 * **Recycling contract.** Before API 33 (Tiramisu) each [AccessibilityNodeInfo]
 * keeps a reference to an underlying parcelable buffer that the consumer is
 * expected to release via `recycle()` — leaking these adds steady GC pressure
 * during polling (e.g. `wait_for_text`). On API 33+ `recycle()` is a no-op,
 * so we call it unconditionally and let the framework decide.
 *
 *  - [withRoot] hands out the active root and recycles it on the way out.
 *  - [walk] recycles every CHILD it dequeues; the root remains owned by the
 *    caller (typically `withRoot`).
 *  - [findOne] returns a single matching node that the caller MUST recycle
 *    after performing its action; siblings traversed along the way are
 *    recycled internally.
 *
 * The projection ([toMap]) masks `text` and `content_description` when the
 * node is flagged as a password field; `is_password` is always reported so a
 * caller can detect masking explicitly.
 */
internal object NodeQuery {

    /**
     * Run [block] with the active-window root, recycling the node afterwards.
     * Returns null when no accessibility service is bound or the root is
     * unavailable.
     */
    inline fun <T> withRoot(block: (AccessibilityNodeInfo) -> T): T? {
        val root = AccessibilityServiceHolder.service?.rootInActiveWindow ?: return null
        return try {
            block(root)
        } finally {
            root.recycle()
        }
    }

    /**
     * Walk [root] breadth-first. [transform] returns true to keep traversing,
     * false to abort the walk early. Every non-root node passed to [transform]
     * is recycled when [transform] returns (or during cleanup if the walk is
     * cancelled). The root itself is owned by the caller.
     */
    inline fun walk(
        root: AccessibilityNodeInfo,
        transform: (node: AccessibilityNodeInfo, depth: Int) -> Boolean,
    ) {
        // Process the root first so the BFS order is "root, then frontier".
        if (!transform(root, 0)) return
        val queue: ArrayDeque<Pair<AccessibilityNodeInfo, Int>> = ArrayDeque()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.addLast(it to 1) }
        }
        try {
            while (queue.isNotEmpty()) {
                val (node, depth) = queue.removeFirst()
                val keepGoing: Boolean
                try {
                    keepGoing = transform(node, depth)
                    if (keepGoing) {
                        for (i in 0 until node.childCount) {
                            node.getChild(i)?.let { queue.addLast(it to depth + 1) }
                        }
                    }
                } finally {
                    node.recycle()
                }
                if (!keepGoing) return
            }
        } finally {
            // Safety net: recycle any nodes left in the queue when we exit
            // abnormally (early return or thrown exception).
            while (queue.isNotEmpty()) queue.removeFirst().first.recycle()
        }
    }

    fun matches(
        node: AccessibilityNodeInfo,
        text: String?,
        viewId: String?,
        className: String?,
        packageName: String?,
    ): Boolean {
        if (text != null) {
            val haystack = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            if (!haystack.contains(text, ignoreCase = true)) return false
        }
        if (viewId != null && node.viewIdResourceName != viewId) return false
        if (className != null && node.className?.toString() != className) return false
        if (packageName != null && node.packageName?.toString() != packageName) return false
        return true
    }

    fun toMap(node: AccessibilityNodeInfo, depth: Int = 0): Map<String, Any?> {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val isPassword = node.isPassword
        return mapOf(
            "view_id" to node.viewIdResourceName,
            "class" to node.className?.toString(),
            "package" to node.packageName?.toString(),
            "text" to if (isPassword) null else node.text?.toString(),
            "content_description" to if (isPassword) null else node.contentDescription?.toString(),
            "is_password" to isPassword,
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom,
            ),
            "clickable" to node.isClickable,
            "long_clickable" to node.isLongClickable,
            "scrollable" to node.isScrollable,
            "editable" to node.isEditable,
            "focused" to node.isFocused,
            "selected" to node.isSelected,
            "enabled" to node.isEnabled,
            "checked" to node.isChecked,
            "depth" to depth,
        )
    }

    /**
     * Find the Nth node in [root] (BFS order) for which [predicate] returns
     * true. Returns null if no such node exists. The returned node is **owned
     * by the caller** and must be recycled; siblings traversed along the way
     * are recycled internally.
     */
    fun findOne(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        index: Int = 0,
    ): AccessibilityNodeInfo? {
        var skipsLeft = index
        // Check root itself — but we never return root (the caller of
        // withRoot already owns and recycles it, and double-recycle is UB).
        // So if root matches we still skip it as a candidate.
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.addLast(it) }
        }
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                var keep = false
                try {
                    val matches = predicate(node)
                    if (matches) {
                        if (skipsLeft == 0) {
                            keep = true
                            return node
                        }
                        skipsLeft--
                    }
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { queue.addLast(it) }
                    }
                } finally {
                    if (!keep) node.recycle()
                }
            }
            return null
        } finally {
            // Safety net: anything left in the queue when we returned (early or
            // via exception) must be recycled.
            while (queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }
}
