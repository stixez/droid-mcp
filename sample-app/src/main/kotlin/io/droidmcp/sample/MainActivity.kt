// sample-app/src/main/kotlin/io/droidmcp/sample/MainActivity.kt
package io.droidmcp.sample

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
        // API 29+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        // API 33+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
) {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date())

    val none = emptyMap<String, Any>()

    // Tool categories: label -> list of (buttonText, toolName, params)
    val categories: List<Pair<String, List<Triple<String, String, Map<String, Any>>>>> = listOf(
        "Device" to listOf(
            Triple("device_info", "get_device_info", none),
            Triple("battery", "get_battery_info", none),
            Triple("connectivity", "get_connectivity", none),
            Triple("storage", "get_storage_info", none),
        ),
        "Calendar" to listOf(
            Triple("read ($today)", "read_calendar", mapOf("date" to today)),
            Triple("search meetings", "search_events", mapOf("query" to "meeting")),
        ),
        "Contacts" to listOf(
            Triple("list", "list_contacts", none),
            Triple("search John", "search_contacts", mapOf("query" to "John")),
        ),
        "SMS" to listOf(
            Triple("read", "read_messages", none),
        ),
        "Files" to listOf(
            Triple("browse", "browse_files", none),
            Triple("search txt", "search_files", mapOf("query" to "txt")),
        ),
        "Call Log" to listOf(
            Triple("read", "read_call_log", none),
        ),
        "Media" to listOf(
            Triple("albums", "list_albums", none),
            Triple("search images", "search_media", mapOf("type" to "images")),
        ),
        "Location" to listOf(
            Triple("location", "get_current_location", none),
        ),
        "Health" to listOf(
            Triple("steps", "get_step_count", none),
            Triple("activity", "get_activity_info", none),
        ),
        "Clipboard" to listOf(
            Triple("read", "read_clipboard", none),
        ),
        "Apps" to listOf(
            Triple("installed", "list_installed_apps", none),
        ),
        "Screen" to listOf(
            Triple("state", "get_screen_state", none),
            Triple("display", "get_display_info", none),
        ),
        "Settings" to listOf(
            Triple("get", "get_settings", none),
        ),
        "Bluetooth" to listOf(
            Triple("status", "get_bluetooth_status", none),
            Triple("paired", "list_paired_devices", none),
        ),
        "WiFi" to listOf(
            Triple("info", "get_wifi_info", none),
        ),
        "Downloads" to listOf(
            Triple("list", "list_downloads", none),
        ),
        "TTS" to listOf(
            Triple("speak", "speak_text", mapOf("text" to "Hello from droid MCP")),
        ),
        "Notifications" to listOf(
            Triple("active", "get_active_notifications", none),
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header
        item {
            Text("DroidMCP Sample", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Registered Tools: ${state.tools.size} / 44",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
        }

        // Controls row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPermissions) { Text("Grant Permissions") }
                if (state.serverRunning) {
                    Button(onClick = onStopServer) { Text("Stop Server") }
                } else {
                    Button(onClick = onStartServer) { Text("Start Server") }
                }
            }
        }

        // Server URL
        state.serverUrl?.let { url ->
            item {
                Spacer(Modifier.height(4.dp))
                Text("Server: $url", style = MaterialTheme.typography.bodyMedium)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Tool categories
        items(categories) { (label, buttons) ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    buttons.forEach { (btnText, toolName, params) ->
                        OutlinedButton(
                            onClick = { onCallTool(toolName, params) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(btnText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Log section header
        item {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text("Tool Call Log:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }

        // Log entries
        items(state.logs) { log ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(log.toolName, style = MaterialTheme.typography.titleSmall)
                    if (log.result.isSuccess) {
                        Text(
                            "OK: ${log.result.data?.toString()?.take(200)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Error: ${log.result.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
