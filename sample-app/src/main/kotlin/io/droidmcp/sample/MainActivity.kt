package io.droidmcp.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.droidmcp.calendar.CalendarTools
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SampleScreen()
            }
        }
    }
}

@Composable
private fun SampleScreen(vm: SampleViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val askCalendar = rememberLauncherForActivityResult(RequestMultiplePermissions()) { vm.refreshTools() }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("droid-mcp sample", style = MaterialTheme.typography.headlineSmall)
            Text("${state.toolCount} tools registered")

            Section("Call a tool on the phone") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.call("get_battery_info") }) { Text("Battery") }
                    if (state.calendarGranted) {
                        OutlinedButton(onClick = { vm.call("read_calendar", mapOf("start_date" to LocalDate.now().toString())) }) {
                            Text("Today's events")
                        }
                    } else {
                        OutlinedButton(onClick = { askCalendar.launch(CalendarTools.requiredPermissions().toTypedArray()) }) {
                            Text("Allow calendar")
                        }
                    }
                }
                state.lastResult?.let { Code(it) }
            }

            Section("Serve to your computer") {
                Button(onClick = vm::toggleServer) { Text(if (state.serverRunning) "Stop server" else "Start server") }
                state.connectCommand?.let {
                    Text("Run this on a computer on the same Wi-Fi:")
                    Code(it)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Code(text: String) {
    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
}
