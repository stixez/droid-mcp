package io.droidmcp.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

private data class ToolButton(
    val label: String,
    val tool: String,
    val params: Map<String, Any> = emptyMap(),
)

private data class ToolCategory(
    val name: String,
    val tools: List<ToolButton>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsPage(onCallTool: (String, Map<String, Any>) -> Unit) {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val categories = listOf(
        ToolCategory("Device", listOf(
            ToolButton("Info", "get_device_info"),
            ToolButton("Battery", "get_battery_info"),
            ToolButton("Network", "get_connectivity"),
            ToolButton("Storage", "get_storage_info"),
        )),
        ToolCategory("Calendar", listOf(
            ToolButton("Today", "read_calendar", mapOf("start_date" to today)),
            ToolButton("Search", "search_events", mapOf("query" to "meeting")),
        )),
        ToolCategory("Contacts", listOf(
            ToolButton("List", "list_contacts"),
            ToolButton("Search", "search_contacts", mapOf("query" to "John")),
        )),
        ToolCategory("SMS", listOf(
            ToolButton("Inbox", "read_messages"),
            ToolButton("Search", "search_messages", mapOf("query" to "hello")),
        )),
        ToolCategory("Files", listOf(
            ToolButton("Browse", "browse_files"),
            ToolButton("Search", "search_files", mapOf("query" to "txt")),
        )),
        ToolCategory("Call Log", listOf(
            ToolButton("Recent", "read_call_log", mapOf("limit" to 5)),
            ToolButton("Search", "search_call_log", mapOf("query" to "555", "limit" to 5)),
        )),
        ToolCategory("Media", listOf(
            ToolButton("Albums", "list_albums"),
            ToolButton("Photos", "search_media", mapOf("media_type" to "images")),
        )),
        ToolCategory("Location", listOf(
            ToolButton("Current", "get_current_location"),
        )),
        ToolCategory("Health", listOf(
            ToolButton("Steps", "get_step_count"),
            ToolButton("Sensors", "get_activity_info"),
        )),
        ToolCategory("Clipboard", listOf(
            ToolButton("Read", "read_clipboard"),
            ToolButton("Write", "write_clipboard", mapOf("text" to "Hello from DroidMCP")),
        )),
        ToolCategory("Apps", listOf(
            ToolButton("Installed", "list_installed_apps"),
        )),
        ToolCategory("Screen", listOf(
            ToolButton("State", "get_screen_state"),
            ToolButton("Display", "get_display_info"),
        )),
        ToolCategory("Settings", listOf(
            ToolButton("Read", "get_settings"),
        )),
        ToolCategory("Bluetooth", listOf(
            ToolButton("Status", "get_bluetooth_status"),
            ToolButton("Paired", "list_paired_devices"),
        )),
        ToolCategory("WiFi", listOf(
            ToolButton("Info", "get_wifi_info"),
        )),
        ToolCategory("Downloads", listOf(
            ToolButton("List", "list_downloads"),
        )),
        ToolCategory("TTS", listOf(
            ToolButton("Speak", "speak_text", mapOf("text" to "Hello from droid MCP")),
            ToolButton("Engines", "get_tts_info"),
        )),
        ToolCategory("Notifications", listOf(
            ToolButton("Active", "get_active_notifications"),
        )),
        ToolCategory("Alarms", listOf(
            ToolButton("Alarm", "create_alarm", mapOf("hour" to 8, "minute" to 0, "message" to "Test alarm")),
            ToolButton("Timer", "create_timer", mapOf("seconds" to 10, "message" to "Test timer")),
            ToolButton("Reminder", "create_reminder", mapOf("title" to "Test reminder", "datetime" to "$today 12:00")),
        )),
        ToolCategory("Telephony", listOf(
            ToolButton("Phone #", "get_phone_number"),
            ToolButton("SIM", "get_sim_info"),
            ToolButton("Operator", "get_network_operator"),
            ToolButton("Call State", "get_call_state"),
        )),
        ToolCategory("Vibration", listOf(
            ToolButton("Vibrate", "vibrate", mapOf("duration_ms" to 200)),
            ToolButton("Pattern", "vibrate_pattern", mapOf("timings" to listOf(0L, 100L, 100L, 200L))),
        )),
        ToolCategory("Flashlight", listOf(
            ToolButton("On", "toggle_flashlight", mapOf("enabled" to true)),
            ToolButton("Off", "toggle_flashlight", mapOf("enabled" to false)),
        )),
        ToolCategory("Biometric", listOf(
            ToolButton("Availability", "check_biometric_availability"),
            ToolButton("Enrollments", "get_biometric_enrollments"),
        )),
        ToolCategory("Network", listOf(
            ToolButton("Data Usage", "get_data_usage"),
            ToolButton("Signal", "get_cellular_signal"),
            ToolButton("VPN", "is_vpn_active"),
        )),
        ToolCategory("Sensors", listOf(
            ToolButton("Accel", "get_accelerometer"),
            ToolButton("Gyro", "get_gyroscope"),
            ToolButton("Light", "get_light_level"),
            ToolButton("Proximity", "get_proximity"),
        )),
        ToolCategory("QR / Barcode", listOf(
            ToolButton("Generate QR", "generate_qr_code", mapOf("text" to "https://droidmcp.io")),
        )),
        ToolCategory("Camera", listOf(
            ToolButton("Photo", "take_photo"),
            ToolButton("Video", "capture_video", mapOf("duration" to 5)),
            ToolButton("Capabilities", "get_camera_capabilities"),
        )),
        ToolCategory("Audio", listOf(
            ToolButton("Devices", "get_audio_devices"),
        )),
        ToolCategory("Web", listOf(
            ToolButton("Search", "web_search", mapOf("query" to "DroidMCP")),
            ToolButton("Fetch", "fetch_webpage", mapOf("url" to "http://example.com")),
        )),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(categories) { category ->
            ToolCategoryCard(category = category, onCallTool = onCallTool)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolCategoryCard(
    category: ToolCategory,
    onCallTool: (String, Map<String, Any>) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                category.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                category.tools.forEach { btn ->
                    AssistChip(
                        onClick = { onCallTool(btn.tool, btn.params) },
                        label = {
                            Text(
                                btn.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        }
    }
}
