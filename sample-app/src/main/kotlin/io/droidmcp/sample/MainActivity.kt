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
        // Re-initialize after permission results
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
        permissionLauncher.launch(arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
        ))
    }

    private fun initViewModel() {
        viewModelRef?.initialize()
    }
}

@Composable
fun MainScreen(
    state: MainState,
    onRequestPermissions: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCallTool: (String, Map<String, Any>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("DroidMCP Sample", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        Text("Registered Tools: ${state.tools.size}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequestPermissions) { Text("Grant Permissions") }
            if (state.serverRunning) {
                Button(onClick = onStopServer) { Text("Stop Server") }
            } else {
                Button(onClick = onStartServer) { Text("Start Server") }
            }
        }

        state.serverUrl?.let {
            Spacer(Modifier.height(8.dp))
            Text("Server: $it", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))

        // Quick test buttons
        Text("Quick Tests:", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { onCallTool("get_device_info", emptyMap()) }) { Text("Device") }
            Button(onClick = { onCallTool("get_battery_info", emptyMap()) }) { Text("Battery") }
            Button(onClick = { onCallTool("get_connectivity", emptyMap()) }) { Text("Network") }
            Button(onClick = { onCallTool("get_storage_info", emptyMap()) }) { Text("Storage") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Tool Call Log:", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.logs) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(log.toolName, style = MaterialTheme.typography.titleSmall)
                        if (log.result.isSuccess) {
                            Text("OK: ${log.result.data}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Error: ${log.result.errorMessage}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
