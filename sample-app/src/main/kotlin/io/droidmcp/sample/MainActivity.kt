package io.droidmcp.sample

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        (results.isNotEmpty()).let { initViewModel() }
    }

    private var viewModelRef: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val vm: MainViewModel = viewModel()
                viewModelRef = vm
                val state by vm.state.collectAsState()

                MainScreen(
                    state = state,
                    onRequestPermissions = { requestAllPermissions() },
                    onStartServer = { vm.startServer() },
                    onStopServer = { vm.stopServer() },
                    onCallTool = { name, params -> vm.callTool(name, params) },
                    onClearLogs = { vm.clearLogs() },
                )
            }
        }

        requestAllPermissions()
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun initViewModel() {
        viewModelRef?.initialize()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: MainState,
    onRequestPermissions: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCallTool: (String, Map<String, Any>) -> Unit,
    onClearLogs: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DroidMCP", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${state.tools.size} tools registered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRequestPermissions,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("Permissions") }
                        if (state.serverRunning) {
                            FilledTonalButton(
                                onClick = onStopServer,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("Stop Server") }
                        } else {
                            Button(
                                onClick = onStartServer,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("Start Server") }
                        }
                    }
                }
                state.serverUrl?.let { url ->
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Tab bar
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Tools") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activity")
                        if (state.logs.isNotEmpty()) {
                            Badge { Text("${state.logs.size}") }
                        }
                    }
                }
            )
        }

        // Pages
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ToolsPage(onCallTool = onCallTool)
                1 -> ActivityPage(logs = state.logs, onClear = onClearLogs)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsPage(onCallTool: (String, Map<String, Any>) -> Unit) {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val none = emptyMap<String, Any>()

    data class ToolButton(val label: String, val tool: String, val params: Map<String, Any> = none)
    data class Category(val name: String, val tools: List<ToolButton>)

    val categories = listOf(
        Category("Device", listOf(
            ToolButton("Info", "get_device_info"),
            ToolButton("Battery", "get_battery_info"),
            ToolButton("Network", "get_connectivity"),
            ToolButton("Storage", "get_storage_info"),
        )),
        Category("Calendar", listOf(
            ToolButton("Today", "read_calendar", mapOf("start_date" to today)),
            ToolButton("Search", "search_events", mapOf("query" to "meeting")),
        )),
        Category("Contacts", listOf(
            ToolButton("List", "list_contacts"),
            ToolButton("Search", "search_contacts", mapOf("query" to "John")),
        )),
        Category("SMS", listOf(
            ToolButton("Inbox", "read_messages"),
            ToolButton("Search", "search_messages", mapOf("query" to "hello")),
        )),
        Category("Files", listOf(
            ToolButton("Browse", "browse_files"),
            ToolButton("Search", "search_files", mapOf("query" to "txt")),
        )),
        Category("Call Log", listOf(
            ToolButton("Recent", "read_call_log"),
            ToolButton("Search", "search_call_log", mapOf("query" to "555")),
        )),
        Category("Media", listOf(
            ToolButton("Albums", "list_albums"),
            ToolButton("Photos", "search_media", mapOf("media_type" to "images")),
        )),
        Category("Location", listOf(
            ToolButton("Current", "get_current_location"),
        )),
        Category("Health", listOf(
            ToolButton("Steps", "get_step_count"),
            ToolButton("Sensors", "get_activity_info"),
        )),
        Category("Clipboard", listOf(
            ToolButton("Read", "read_clipboard"),
            ToolButton("Write", "write_clipboard", mapOf("text" to "Hello from DroidMCP")),
        )),
        Category("Apps", listOf(
            ToolButton("Installed", "list_installed_apps"),
        )),
        Category("Screen", listOf(
            ToolButton("State", "get_screen_state"),
            ToolButton("Display", "get_display_info"),
        )),
        Category("Settings", listOf(
            ToolButton("Read", "get_settings"),
        )),
        Category("Bluetooth", listOf(
            ToolButton("Status", "get_bluetooth_status"),
            ToolButton("Paired", "list_paired_devices"),
        )),
        Category("WiFi", listOf(
            ToolButton("Info", "get_wifi_info"),
        )),
        Category("Downloads", listOf(
            ToolButton("List", "list_downloads"),
        )),
        Category("TTS", listOf(
            ToolButton("Speak", "speak_text", mapOf("text" to "Hello from droid MCP")),
            ToolButton("Engines", "get_tts_info"),
        )),
        Category("Notifications", listOf(
            ToolButton("Active", "get_active_notifications"),
        )),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { category ->
            Column {
                Text(
                    category.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    category.tools.forEach { btn ->
                        FilledTonalButton(
                            onClick = { onCallTool(btn.tool, btn.params) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(btn.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityPage(logs: List<ToolCallLog>, onClear: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No activity yet.\nTap a tool to see results here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${logs.size} calls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogCard(log = log, timeFormat = timeFormat)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: ToolCallLog, timeFormat: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (log.result.isSuccess)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    log.toolName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    timeFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            if (log.result.isSuccess) {
                val text = log.result.data?.toString() ?: ""
                Text(
                    if (expanded) text else text.take(120) + if (text.length > 120) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (text.length > 120 && !expanded) {
                    Text(
                        "Tap to expand",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else {
                Text(
                    log.result.errorMessage ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (log.params.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "params: ${log.params}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
