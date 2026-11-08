package io.droidmcp.app.feature.home

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.server.ConnectionInfo
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.app.ui.grant.rememberGrantLauncher
import io.droidmcp.app.ui.components.Banner
import io.droidmcp.app.ui.components.BannerTone
import io.droidmcp.app.ui.components.CodeBlock
import io.droidmcp.app.ui.components.ScreenScaffold
import io.droidmcp.app.ui.components.StatusDot
import io.droidmcp.app.ui.copyText
import io.droidmcp.app.ui.relativeTime
import io.droidmcp.app.ui.grant.startSafely
import kotlinx.coroutines.delay
import io.droidmcp.app.ui.theme.AppTheme

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenComputers: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // Coming back from the grant brings the app to the front, which checks the network again.
    val launchGrant = rememberGrantLauncher(onReturn = {})

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Copy -> {
                    val confirmHere = context.copyText(effect.what, effect.text, effect.sensitive)
                    if (confirmHere) snackbar.showSnackbar("${effect.what} copied")
                }
                HomeEffect.NavigatePermissions -> onOpenPermissions()
                HomeEffect.NavigateActivity -> onOpenActivity()
                HomeEffect.NavigateComputers -> onOpenComputers()
                is HomeEffect.OpenUrl -> context.startSafely(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                is HomeEffect.LaunchGrant -> launchGrant(effect.capability)
            }
        }
    }

    ScreenScaffold(
        title = "droid-mcp",
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        },
    ) { padding ->
        HomeContent(state = state, onIntent = viewModel::onIntent, contentPadding = padding)
    }
}

@Composable
fun HomeContent(state: HomeState, onIntent: (HomeIntent) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") { StatusHero(state, onToggle = { onIntent(HomeIntent.ToggleServer) }) }
        if (state.pausedWhileClosed) {
            item(key = "paused-while-closed") {
                Banner(
                    text = "Paused while the app was closed, because Android hid the Wi-Fi name. Allow location " +
                        "“All the time” to let it check by itself. Only the network name is read.",
                    icon = Icons.Outlined.Wifi,
                    tone = BannerTone.Info,
                    actionLabel = "Allow",
                    onAction = { onIntent(HomeIntent.AllowBackgroundLocation) },
                    dismissLabel = "Not now",
                    onDismiss = { onIntent(HomeIntent.DismissPausedWhileClosed) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // Banners are separate items only while shown, so hidden ones leave no gaps between cards.
        if (state.restartNeeded) {
            item(key = "restart") {
                Banner(
                    text = "Settings changed. Restart the server to apply them.",
                    icon = Icons.Outlined.Restore,
                    tone = BannerTone.Attention,
                    actionLabel = "Restart",
                    onAction = { onIntent(HomeIntent.Restart) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (state.missingEssentials.isNotEmpty() && !state.essentialsHintDismissed) {
            item(key = "essentials") {
                val names = state.missingEssentials.map { Capabilities.get(it).title.lowercase() }
                Banner(
                    text = "Allow ${names.joinToString(" and ")} to get the most out of droid-mcp.",
                    icon = Icons.Outlined.Shield,
                    tone = BannerTone.Info,
                    actionLabel = "Allow",
                    onAction = { onIntent(HomeIntent.OpenPermissions) },
                    dismissLabel = "Not now",
                    onDismiss = { onIntent(HomeIntent.DismissEssentialsHint) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        state.updateAvailable?.let { version ->
            item(key = "update") {
                Banner(
                    text = "droid-mcp $version is available.",
                    icon = Icons.Outlined.SystemUpdate,
                    tone = BannerTone.Info,
                    actionLabel = "Download",
                    onAction = { onIntent(HomeIntent.OpenUpdate) },
                    dismissLabel = "Later",
                    onDismiss = { onIntent(HomeIntent.DismissUpdate) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "connect") { ConnectCard(state, onIntent) }
        item(key = "activity") { ActivityCard(state, onOpen = { onIntent(HomeIntent.OpenActivity) }) }
        if (state.examples.isNotEmpty()) {
            item(key = "examples") { ExamplesCard(state.examples, onCopy = { onIntent(HomeIntent.CopyExample(it)) }) }
        }
    }
}

@Composable
private fun StatusHero(state: HomeState, onToggle: () -> Unit) {
    val status = state.status
    val container by animateColorAsState(
        when (status) {
            is ServerStatus.Running -> MaterialTheme.colorScheme.primaryContainer
            is ServerStatus.Paused -> MaterialTheme.colorScheme.tertiaryContainer
            is ServerStatus.Failed -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "hero",
    )
    val content = when (status) {
        is ServerStatus.Running -> MaterialTheme.colorScheme.onPrimaryContainer
        is ServerStatus.Paused -> MaterialTheme.colorScheme.onTertiaryContainer
        is ServerStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = RoundedCornerShape(28.dp), color = container, contentColor = content) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dot, label) = when (status) {
                    is ServerStatus.Running -> MaterialTheme.colorScheme.primary to "On"
                    ServerStatus.Starting -> MaterialTheme.colorScheme.tertiary to "Starting"
                    is ServerStatus.Paused -> MaterialTheme.colorScheme.tertiary to "Paused"
                    is ServerStatus.Failed -> MaterialTheme.colorScheme.error to "Error"
                    ServerStatus.Stopped -> MaterialTheme.colorScheme.outline to "Off"
                }
                StatusDot(dot, pulsing = status is ServerStatus.Running)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (status) {
                    is ServerStatus.Running -> "Ready for your AI"
                    ServerStatus.Starting -> "Starting the server"
                    is ServerStatus.Paused -> "Waiting for a trusted Wi-Fi"
                    is ServerStatus.Failed -> "The server couldn't start"
                    ServerStatus.Stopped -> "Server is off"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            when (status) {
                is ServerStatus.Running -> {
                    val host = when (state.route) {
                        Route.Usb -> "localhost"
                        Route.Wifi -> state.network?.address ?: "no network"
                    }
                    Text(
                        "$host:${status.port} · ${status.toolCount} tools",
                        style = AppTheme.code.medium,
                        color = content.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(10.dp))
                    ConnectionLine(state, content)
                }
                is ServerStatus.Paused -> Text(
                    // Some reasons already end with a full stop ("... turn on Location.").
                    status.reason.removePrefix("Paused: ").trimEnd('.').replaceFirstChar { it.uppercase() } +
                        ". It starts again by itself on a trusted network.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ServerStatus.Failed -> Text(status.reason, style = MaterialTheme.typography.bodyMedium)
                else -> Text(
                    if (status == ServerStatus.Starting) {
                        "This takes a moment, longer the first time HTTPS is on."
                    } else {
                        "Turn it on, then connect from your computer. It keeps running when you leave the app."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(20.dp))
            when (status) {
                is ServerStatus.Running, is ServerStatus.Paused -> OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("Stop server", color = content) }
                ServerStatus.Starting -> OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
                    Spacer(Modifier.width(12.dp))
                    Text("Starting \u00b7 Cancel", color = content)
                }
                else -> Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(if (status is ServerStatus.Failed) "Try again" else "Start server") }
            }
        }
    }
}

@Composable
private fun ConnectCard(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp)) {
            Text("Connect a client", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ClientKind.entries.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = state.client == kind,
                        onClick = { onIntent(HomeIntent.SelectClient(kind)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ClientKind.entries.size),
                        icon = {},
                    ) { Text(kind.label, maxLines = 1) }
                }
            }

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Route.entries.forEach { route ->
                    FilterChip(
                        selected = state.route == route,
                        onClick = { onIntent(HomeIntent.SelectRoute(route)) },
                        label = { Text(route.label) },
                    )
                }
            }

            if (!state.isRunning) {
                Spacer(Modifier.height(12.dp))
                Banner(
                    text = if (state.status is ServerStatus.Paused) {
                        "The server is paused until the phone is on a trusted Wi-Fi."
                    } else {
                        "Start the server first. The command works once it's on."
                    },
                    icon = Icons.Outlined.PowerSettingsNew,
                    tone = BannerTone.Info,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                instructionFor(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val preview = state.configPreview
            if (preview != null) {
                CodeBlock(preview, wrap = state.client == ClientKind.ClaudeCode)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onIntent(HomeIntent.CopyConfig) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.client == ClientKind.ClaudeCode) "Copy command" else "Copy config")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { onIntent(HomeIntent.ToggleTokenVisible) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (state.tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (state.tokenVisible) "Hide token" else "Show token",
                        )
                    }
                }
            } else if (state.connectionKnown) {
                Banner(
                    text = "Connect the phone to the same Wi-Fi as your computer, or switch to USB.",
                    icon = Icons.Outlined.WifiOff,
                    tone = BannerTone.Attention,
                )
            }

            if (state.offWifi) {
                Spacer(Modifier.height(12.dp))
                Banner(
                    text = "The phone isn't on Wi-Fi, so your computer probably can't reach this address. Join the same Wi-Fi or use USB.",
                    icon = Icons.Outlined.WifiOff,
                    tone = BannerTone.Attention,
                )
            }

            if (state.route == Route.Usb) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Usb, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("First run on your computer:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CodeBlock(ConnectionInfo.adbForwardCommand(state.activePort), Modifier.weight(1f))
                    IconButton(onClick = { onIntent(HomeIntent.CopyAdbForward) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy adb command")
                    }
                }
            }

            TextButton(
                onClick = { onIntent(HomeIntent.OpenComputers) },
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Outlined.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Give each computer its own token")
            }

            state.connection?.let { connection ->
                Spacer(Modifier.height(16.dp))
                DetailRow(label = "Address", value = connection.url, onCopy = { onIntent(HomeIntent.CopyUrl) })
                DetailRow(
                    label = "Token",
                    value = if (state.tokenVisible) connection.token else HomeState.mask(connection.token),
                    onCopy = { onIntent(HomeIntent.CopyToken) },
                )
            }
        }
    }
}

private fun instructionFor(state: HomeState): String = when (state.client) {
    ClientKind.ClaudeCode -> "Run this in a terminal on your computer. Claude Code can then use the phone in any session."
    ClientKind.Codex -> "Add this to ~/.codex/config.toml on your computer."
    ClientKind.Json -> "Paste this into your client's MCP config, for example ~/.cursor/mcp.json for Cursor."
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = AppTheme.code.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label") }
    }
}

@Composable
private fun ActivityCard(state: HomeState, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Activity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                val last = state.lastCall
                if (last == null) {
                    Text(
                        "No calls yet. Try asking your AI \"What's on my calendar today?\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${state.callsToday}${if (state.callsTodayCapped) "+" else ""} ${if (state.callsToday == 1) "call" else "calls"} today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        if (!last.success) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(last.toolName, style = AppTheme.code.label)
                        Text(
                            " · " + relativeTime(last.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Is it connected?": the latest call, or a clear note that nothing has called yet. */
@Composable
private fun ConnectionLine(state: HomeState, content: Color) {
    val last = state.lastCall
    // Re-read the clock every 30 s so "2 min ago" stays true without new calls.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.waitingForFirstCall || last == null) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = content.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Text(
                "Waiting for the first call from your computer",
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.85f),
            )
        } else {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
            Spacer(Modifier.width(8.dp))
            Text(
                // Minute steps: the clock above only ticks every 30 s, so seconds would be stale.
                "Connected · last call ${relativeTime(last.timestamp, maxOf(now, last.timestamp))}",
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.85f),
            )
        }
    }
}

/** Example prompts for what's allowed; tap to copy one into the AI chat. */
@Composable
private fun ExamplesCard(examples: List<String>, onCopy: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("Try asking", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Tap one to copy it, then paste it into your AI chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            examples.forEach { prompt ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onCopy(prompt) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("\u201c$prompt\u201d", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

