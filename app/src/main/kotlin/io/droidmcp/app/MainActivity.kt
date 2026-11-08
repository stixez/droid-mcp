package io.droidmcp.app

import android.app.UiModeManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import io.droidmcp.app.data.settings.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.confirm.ApprovalScope
import io.droidmcp.app.confirm.ConfirmationCenter
import io.droidmcp.app.ui.navigation.AppShell
import io.droidmcp.app.ui.theme.AppTheme
import io.droidmcp.app.ui.theme.DroidMcpTheme

class MainActivity : ComponentActivity() {

    /**
     * Android 12+ remembers the app's night mode, so the window background and system dialogs
     * (permission prompts) match the choice too, and the next launch starts in the right colors.
     */
    private fun applyNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = getSystemService(UiModeManager::class.java) ?: return
        val night = when (mode) {
            ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
        }
        manager.setApplicationNightMode(night)
    }

    private val graph get() = appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Null until DataStore has loaded: acting on the defaults would flip the night mode
            // (and recreate the activity) on every cold start.
            val loaded by graph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = loaded?.themeMode
            val dark = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System, null -> isSystemInDarkTheme()
            }
            // Status and navigation bar icons must contrast with the app's theme, not the phone's.
            DisposableEffect(dark) {
                val transparent = Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                    navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                )
                onDispose {}
            }
            LaunchedEffect(themeMode) { themeMode?.let(::applyNightMode) }
            DroidMcpTheme(darkTheme = dark) {
                // Read the saved settings once to pick the first screen; the window background
                // covers the few milliseconds this takes.
                val startOnSetup by produceState<Boolean?>(null) {
                    value = !graph.settings.snapshot().setupCompleted
                }
                startOnSetup?.let { AppShell(graph, startOnSetup = it) }
                ConfirmationDialogHost(graph.confirmations)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        graph.foreground.onStart()
        graph.appScope.launch { graph.updates.checkIfDue() }
    }

    override fun onResume() {
        super.onResume()
        graph.foreground.onResume(this)
        graph.permissions.refresh()
    }

    override fun onPause() {
        graph.foreground.onPause(this)
        super.onPause()
    }

    override fun onStop() {
        // Rotation or a theme switch recreates the activity; that isn't the user leaving.
        if (!isChangingConfigurations) graph.foreground.onStop()
        super.onStop()
    }
}

/** Shows the oldest waiting approval while the app is open (otherwise it's a notification). */
@Composable
private fun ConfirmationDialogHost(center: ConfirmationCenter) {
    val pending by center.pending.collectAsStateWithLifecycle()
    val request = pending.firstOrNull() ?: return
    // Counts down to the automatic Deny, so the user knows how long they have.
    var now by remember(request.id) { mutableLongStateOf(System.currentTimeMillis()) }
    // A new request takes the old one's place with buttons in the same spots: ignore taps briefly,
    // so a double tap meant for the previous request can't approve one the user never saw.
    var armed by remember(request.id) { mutableStateOf(false) }
    LaunchedEffect(request.id) {
        launch {
            delay(ARM_DELAY_MS)
            armed = true
        }
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val remainingMs = (request.expiresAt - now).coerceAtLeast(0L)
    // Keyed by request, so the next one is a fresh dialog rather than new text in the old one.
    key(request.id) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(request.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${request.requester} is asking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(request.detail, style = AppTheme.code.medium, modifier = Modifier.padding(top = 12.dp))
                    LinearProgressIndicator(
                        progress = { remainingMs / center.timeout.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { center.answer(request.id, allow = true, scope = ApprovalScope.OneHour) },
                            enabled = armed,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Allow 1 hour", maxLines = 1) }
                        OutlinedButton(
                            onClick = { center.answer(request.id, allow = true, scope = ApprovalScope.Always) },
                            enabled = armed,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Always allow", maxLines = 1) }
                    }
                    Text(
                        "Declined automatically in ${(remainingMs + 999) / 1000} s" +
                            if (pending.size > 1) " · ${pending.size - 1} more waiting" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { center.answer(request.id, allow = true) }, enabled = armed) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { center.answer(request.id, allow = false) }, enabled = armed) { Text("Deny") }
            },
        )
    }
}

private const val ARM_DELAY_MS = 400L
