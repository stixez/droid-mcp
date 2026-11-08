package io.droidmcp.app.feature.activity

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.ui.components.CodeBlock
import io.droidmcp.app.ui.copyText
import io.droidmcp.app.ui.components.ScreenScaffold
import io.droidmcp.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date

@Composable
fun ActivityRoute(viewModel: ActivityViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ActivityEffect.ShareJson -> context.shareJson(effect.json)
                ActivityEffect.DeleteExports -> withContext(Dispatchers.IO) { context.deleteExports() }
                is ActivityEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    ScreenScaffold(
        title = "Activity",
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { viewModel.onIntent(ActivityIntent.Export) }, enabled = state.items.isNotEmpty()) {
                Icon(Icons.Outlined.IosShare, contentDescription = "Export as JSON")
            }
            IconButton(onClick = { viewModel.onIntent(ActivityIntent.AskClear) }, enabled = state.items.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear activity")
            }
        },
    ) { padding ->
        ActivityContent(state, viewModel::onIntent, padding)
    }

    state.selected?.let { item ->
        DetailSheet(item, onDismiss = { viewModel.onIntent(ActivityIntent.DismissDetail) })
    }
    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ActivityIntent.CancelClear) },
            title = { Text("Clear activity?") },
            text = { Text("This deletes the record of every call on this phone. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(ActivityIntent.ConfirmClear) }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(ActivityIntent.CancelClear) }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityContent(state: ActivityState, onIntent: (ActivityIntent) -> Unit, contentPadding: PaddingValues) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.items.isEmpty()) {
        EmptyActivity(Modifier.padding(contentPadding))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 24.dp),
    ) {
        item(key = "filters") {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onIntent(ActivityIntent.SetFilter(filter)) },
                        label = {
                            Text(if (filter == ActivityFilter.Failed) "Failed (${state.failedCount})" else filter.label)
                        },
                    )
                }
            }
        }
        state.days.forEach { day ->
            stickyHeader(key = "day-${day.dayStart}") {
                Text(
                    dayLabel(day.dayStart),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            items(day.items, key = { it.key }) { item ->
                ActivityRow(item, onClick = { onIntent(ActivityIntent.Select(item)) })
            }
        }
        if (state.visible.isEmpty()) {
            item(key = "none-failed") {
                Text(
                    "No failed calls.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (container, tint, icon) = when {
            item.success -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Outlined.Check)
            item.declined -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Outlined.Block)
            else -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Outlined.ErrorOutline)
        }
        Box(
            Modifier
                .size(32.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = when {
                    item.success -> "Succeeded"
                    item.declined -> "Declined"
                    else -> "Failed"
                },
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.toolName, style = AppTheme.code.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    item.declined -> "You declined this"
                    !item.success && item.error != null -> item.error
                    else -> listOfNotNull(clientName(item.client), "${item.durationMs} ms").joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    item.success -> MaterialTheme.colorScheme.onSurfaceVariant
                    item.declined -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            DateFormat.getTimeFormat(context).format(Date(item.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(item: ActivityItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            Text(item.toolName, style = AppTheme.code.label.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    item.success -> "Succeeded"
                    item.declined -> "Declined on the phone"
                    else -> "Failed"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    item.success -> MaterialTheme.colorScheme.primary
                    item.declined -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.height(16.dp))
            DetailLine("When", DateUtils.formatDateTime(context, item.timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME))
            DetailLine("Client", clientName(item.client) ?: "This phone")
            DetailLine("Took", "${item.durationMs} ms")
            if (item.error != null) {
                Spacer(Modifier.height(16.dp))
                SheetLabel("Error", copyText = item.error)
                Text(item.error, style = AppTheme.code.medium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            SheetLabel("Arguments", copyText = item.arguments)
            CodeBlock(item.arguments ?: "None", wrap = true)
        }
    }
}

/** Section label in the detail sheet, with a copy button when there's something to copy. */
@Composable
private fun SheetLabel(label: String, copyText: String?) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (copyText != null) {
            IconButton(onClick = { context.copyText(label, copyText, sensitive = false) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy ${label.lowercase()}", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyActivity(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text("Nothing yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Every tool a client calls shows up here, with what it asked for and how it went. Kept for 7 days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun clientName(label: String?): String? = when (label) {
    null -> null
    "primary" -> "Shared token"
    "anonymous" -> "Unauthenticated client"
    else -> label
}

@Composable
private fun dayLabel(dayStart: Long): String {
    val context = LocalContext.current
    return when {
        DateUtils.isToday(dayStart) -> "Today"
        DateUtils.isToday(Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis) -> "Yesterday"
        else -> DateUtils.formatDateTime(context, dayStart, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
    }
}

/** Exports hold message text and contact names; keep only the newest, and none after a clear. */
private fun Context.deleteExports() {
    File(cacheDir, EXPORT_DIR).listFiles()?.forEach { it.delete() }
}

private suspend fun Context.shareJson(json: String) {
    val file = withContext(Dispatchers.IO) {
        deleteExports()
        val dir = File(cacheDir, EXPORT_DIR).apply { mkdirs() }
        File(dir, "droid-mcp-activity-${System.currentTimeMillis()}.json").apply { writeText(json) }
    }
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, "Export activity"))
}

private const val EXPORT_DIR = "exports"
