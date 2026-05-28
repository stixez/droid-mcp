package io.droidmcp.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.droidmcp.core.McpTool

/**
 * Per-tool gating grid (0.10.0 hardening). Each registered tool has a switch;
 * flipping it off hides the tool from `tools/list` and makes `tools/call` reject
 * it — live, without restarting the server. Backed by `DroidMcp.setToolEnabled`.
 *
 * Bulk actions are scoped to the current filter: "Disable all" while a filter is
 * active only touches the tools currently in view, never the whole registry.
 */
@Composable
fun GatingPage(
    tools: List<McpTool>,
    disabledTools: Set<String>,
    onToggleTool: (String, Boolean) -> Unit,
    onSetMany: (Set<String>, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val sorted = remember(tools) { tools.sortedBy { it.name } }
    val filtered = remember(sorted, query) {
        if (query.isBlank()) sorted
        else sorted.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }
    // Defensive against any disabled name that isn't currently registered.
    val enabledCount = tools.count { it.name !in disabledTools }
    val filtering = query.isNotBlank()
    val anyVisibleDisabled = filtered.any { it.name in disabledTools }
    val anyVisibleEnabled = filtered.any { it.name !in disabledTools }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Summary + bulk actions ─────────────────────────────────────────
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$enabledCount of ${tools.size} tools enabled",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { onSetMany(filtered.map { it.name }.toSet(), true) },
                            enabled = anyVisibleDisabled,
                        ) { Text(if (filtering) "Enable shown" else "Enable all") }
                        TextButton(
                            onClick = { onSetMany(filtered.map { it.name }.toSet(), false) },
                            enabled = anyVisibleEnabled,
                        ) { Text(if (filtering) "Disable shown" else "Disable all") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Filter tools") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
        }

        // ── Tool list ──────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (tools.isEmpty()) "No tools registered"
                    else "No tools match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.name }) { tool ->
                    ToolGateRow(
                        tool = tool,
                        enabled = tool.name !in disabledTools,
                        onToggle = { on -> onToggleTool(tool.name, on) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolGateRow(
    tool: McpTool,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    tool.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
