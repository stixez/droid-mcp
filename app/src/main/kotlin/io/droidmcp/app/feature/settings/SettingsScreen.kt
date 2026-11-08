package io.droidmcp.app.feature.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.R
import io.droidmcp.app.data.settings.ThemeMode
import io.droidmcp.app.ui.components.Banner
import io.droidmcp.app.ui.components.BannerTone
import io.droidmcp.app.ui.components.ScreenScaffold
import io.droidmcp.app.ui.components.SectionHeader
import io.droidmcp.app.ui.components.RowDivider
import io.droidmcp.app.ui.components.SettingsGroup
import io.droidmcp.app.ui.components.SwitchRow
import io.droidmcp.app.ui.grant.rememberGrantLauncher
import io.droidmcp.app.ui.grant.startSafely
import io.droidmcp.app.ui.theme.AppTheme

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenComputers: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // MainActivity re-checks permissions on resume, which also covers coming back from the dialog.
    val grant = rememberGrantLauncher(onReturn = {})

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigatePermissions -> onOpenPermissions()
                is SettingsEffect.LaunchGrant -> grant(effect.capability)
                SettingsEffect.NavigateComputers -> onOpenComputers()
                is SettingsEffect.OpenUrl -> context.startSafely(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                is SettingsEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    ScreenScaffold(
        title = "Settings",
        snackbarHostState = snackbar,
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        },
    ) { padding ->
        SettingsContent(state, viewModel::onIntent, padding)
    }

    if (state.confirmRotate) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(SettingsIntent.CancelRotateToken) },
            title = { Text("Create a new token?") },
            text = { Text("Every connected client stops working until you copy the new command to it. Use this if the token leaked.") },
            confirmButton = { TextButton(onClick = { viewModel.onIntent(SettingsIntent.ConfirmRotateToken) }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { viewModel.onIntent(SettingsIntent.CancelRotateToken) }) { Text("Cancel") } },
        )
    }
    if (state.showLicenses) {
        LicensesDialog(onDismiss = { viewModel.onIntent(SettingsIntent.HideLicenses) })
    }
}

@Composable
fun SettingsContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(), bottom = 32.dp),
    ) {
        item(key = "restart") {
            AnimatedVisibility(state.restartNeeded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Banner(
                    text = "Restart the server to apply your changes.",
                    icon = Icons.Outlined.Restore,
                    tone = BannerTone.Attention,
                    actionLabel = "Restart",
                    onAction = { onIntent(SettingsIntent.Restart) },
                )
            }
        }

        item(key = "appearance-header") { SectionHeader("Appearance") }
        item(key = "appearance") {
            SettingsGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "System follows your phone's dark theme setting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { onIntent(SettingsIntent.SetThemeMode(mode)) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                icon = {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.System -> Icons.Outlined.BrightnessAuto
                                            ThemeMode.Light -> Icons.Outlined.LightMode
                                            ThemeMode.Dark -> Icons.Outlined.DarkMode
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                },
                            ) { Text(mode.name) }
                        }
                    }
                }
            }
        }

        item(key = "access-header") { SectionHeader("Access") }
        item(key = "access") {
            SettingsGroup {
                NavRow("Permissions", "What clients are allowed to use on this phone", onClick = { onIntent(SettingsIntent.OpenPermissions) })
                RowDivider()
                NavRow(
                    "Computers",
                    when (state.computerCount) {
                        0 -> "Give each computer its own token"
                        1 -> "1 computer with its own token"
                        else -> "${state.computerCount} computers with their own tokens"
                    },
                    onClick = { onIntent(SettingsIntent.OpenComputers) },
                )
                RowDivider()
                NavRow("Create a new token", "Disconnects every client using the shared token", onClick = { onIntent(SettingsIntent.AskRotateToken) })
            }
        }

        item(key = "approvals-header") { SectionHeader("Approvals", subtitle = "Tools that run without asking you first") }
        item(key = "approvals") {
            SettingsGroup {
                if (state.alwaysAllowed.isEmpty() && state.temporarilyAllowed == 0) {
                    Text(
                        "Nothing yet. When you tap \"Always allow\" on an approval, the tool shows up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                state.alwaysAllowed.forEachIndexed { index, tool ->
                    if (index > 0) RowDivider()
                    RemovableRow(
                        title = tool,
                        subtitle = "Always allowed",
                        mono = true,
                        removeLabel = "Ask again for $tool",
                        onRemove = { onIntent(SettingsIntent.ForgetAlwaysAllowed(tool)) },
                    )
                }
                if (state.temporarilyAllowed > 0) {
                    if (state.alwaysAllowed.isNotEmpty()) RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.temporarilyAllowed == 1) "1 tool allowed for the next hour" else "${state.temporarilyAllowed} tools allowed for the next hour",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onIntent(SettingsIntent.ForgetTemporaryApprovals) }) { Text("Ask again") }
                    }
                }
            }
        }

        item(key = "server-header") { SectionHeader("Server") }
        item(key = "server") {
            SettingsGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("Port", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Clients connect to this port over Wi-Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                        OutlinedTextField(
                            value = state.portInput,
                            onValueChange = { onIntent(SettingsIntent.EditPort(it)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = AppTheme.code.medium,
                            isError = state.portError != null,
                            supportingText = state.portError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onIntent(SettingsIntent.SavePort) }),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { onIntent(SettingsIntent.SavePort) },
                            enabled = state.portChanged,
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text("Save") }
                    }
                }
                RowDivider()
                SwitchRow(
                    title = "HTTPS",
                    subtitle = "Encrypts traffic with a self-signed certificate on port 8443. Only for clients that can pin a certificate; Claude Code can't yet.",
                    checked = state.tls,
                    onChange = { onIntent(SettingsIntent.SetTls(it)) },
                )
                state.tlsFingerprint?.let { fingerprint ->
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Text("Certificate fingerprint (SHA-256)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fingerprint, style = AppTheme.code.small)
                    }
                }
                RowDivider()
                SwitchRow(
                    title = "Start when the phone starts",
                    subtitle = "Turns the server on after a restart, so you never have to open the app.",
                    checked = state.startOnBoot,
                    onChange = { onIntent(SettingsIntent.SetStartOnBoot(it)) },
                )
            }
        }

        item(key = "network-header") { SectionHeader("Network") }
        item(key = "network") {
            SettingsGroup {
                SwitchRow(
                    title = "Trusted Wi-Fi only",
                    subtitle = "Pauses the server on any other network, like a café or hotel Wi-Fi, and resumes at home.",
                    checked = state.trustedWifiOnly,
                    onChange = { onIntent(SettingsIntent.SetTrustedWifiOnly(it)) },
                )
                if (state.trustedWifiOnly) {
                    state.trustedNetworks.forEach { ssid ->
                        RowDivider()
                        RemovableRow(
                            title = ssid,
                            subtitle = if (ssid == state.currentSsid) "Trusted · connected now" else "Trusted",
                            removeLabel = "Stop trusting $ssid",
                            onRemove = { onIntent(SettingsIntent.ForgetNetwork(ssid)) },
                        )
                    }
                    val ssid = state.currentSsid
                    when {
                        ssid != null && !state.currentNetworkTrusted -> {
                            RowDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ssid, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Connected now · not trusted",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onIntent(SettingsIntent.TrustCurrentNetwork) }) { Text("Trust") }
                            }
                        }
                        ssid == null && state.onWifi -> {
                            RowDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Android hides the Wi-Fi name until droid-mcp has Wi-Fi details access and Location is on.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onIntent(SettingsIntent.AllowWifiDetails) }) { Text("Allow") }
                            }
                        }
                    }
                }
            }
        }

        item(key = "shell-header") { SectionHeader("Shell tools") }
        item(key = "shell") {
            SettingsGroup {
                SwitchRow(
                    title = "Safe shell policy",
                    subtitle = "Shizuku and root tools refuse sensitive settings and powerful permission grants. Turn off only if you know why.",
                    checked = state.strictShellPolicy,
                    onChange = { onIntent(SettingsIntent.SetStrictShellPolicy(it)) },
                )
            }
        }

        item(key = "about-header") { SectionHeader("About") }
        item(key = "about") {
            SettingsGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("droid-mcp", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${state.versionName} · Apache 2.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowDivider()
                SwitchRow(
                    title = "Check for updates",
                    subtitle = "Once a day, asks GitHub whether a newer version is out. Nothing else is sent.",
                    checked = state.checkForUpdates,
                    onChange = { onIntent(SettingsIntent.SetCheckForUpdates(it)) },
                )
                RowDivider()
                NavRow(
                    "Check now",
                    if (state.checkingForUpdates) "Checking\u2026" else null,
                    onClick = { onIntent(SettingsIntent.CheckForUpdatesNow) },
                )
                RowDivider()
                NavRow("Source code", "github.com/stixez/droid-mcp", external = true, onClick = { onIntent(SettingsIntent.OpenSource) })
                RowDivider()
                NavRow("Open source licences", null, onClick = { onIntent(SettingsIntent.ShowLicenses) })
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            if (external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}



@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val ofl = remember(resources) {
        resources.openRawResource(R.raw.jetbrains_mono_ofl).bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open source licences") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "droid-mcp is Apache 2.0. It builds on Kotlin, Jetpack Compose and AndroidX, Ktor and Netty (Apache 2.0), " +
                        "BouncyCastle (MIT), Shizuku (Apache 2.0), libsu (Apache 2.0), ZXing (Apache 2.0) and Google ML Kit " +
                        "(ML Kit terms).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.app_name) + " uses JetBrains Mono:", style = MaterialTheme.typography.titleSmall)
                Text(ofl, style = AppTheme.code.small, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A list row with a remove button, for trusted networks and remembered approvals. */
@Composable
private fun RemovableRow(title: String, subtitle: String, removeLabel: String, onRemove: () -> Unit, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = if (mono) AppTheme.code.label else MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = removeLabel) }
    }
}

