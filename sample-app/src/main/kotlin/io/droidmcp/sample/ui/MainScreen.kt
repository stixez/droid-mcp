package io.droidmcp.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import io.droidmcp.sample.MainState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainState,
    onRequestPermissions: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCallTool: (String, Map<String, Any>) -> Unit,
    onClearLogs: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // ── Top Header ──────────────────────────────────────────────────────
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "DroidMCP",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "${state.tools.size} tools registered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Permissions icon button
                        IconButton(onClick = onRequestPermissions) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = "Request permissions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Server status chip
                        if (state.serverRunning) {
                            FilterChip(
                                selected = true,
                                onClick = onStopServer,
                                label = { Text("Running") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        } else {
                            FilterChip(
                                selected = false,
                                onClick = onStartServer,
                                label = { Text("Stopped") },
                            )
                        }
                    }
                }

                // Server URL pill
                state.serverUrl?.let { url ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // ── Tab Row ─────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Tools") },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Activity")
                        if (state.logs.isNotEmpty()) {
                            Badge { Text("${state.logs.size}") }
                        }
                    }
                },
            )
        }

        // ── Pages ────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> ToolsPage(onCallTool = onCallTool)
                1 -> ActivityPage(logs = state.logs, onClear = onClearLogs)
            }
        }
    }
}
