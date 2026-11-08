package io.droidmcp.app.feature.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.CapabilitySection
import io.droidmcp.app.ui.AppIcons
import io.droidmcp.app.ui.components.MaxContentWidth
import io.droidmcp.app.ui.components.SettingsGroup
import io.droidmcp.app.ui.components.SectionHeader
import io.droidmcp.app.ui.grant.openAppInfo
import io.droidmcp.app.ui.grant.rememberGrantLauncher

@Composable
fun SetupRoute(viewModel: SetupViewModel, onFinished: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val grant = rememberGrantLauncher(onReturn = { viewModel.onIntent(SetupIntent.Refresh) })

    // Most grants happen in system Settings, which has no result callback: re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(SetupIntent.Refresh) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SetupEffect.LaunchGrant -> grant(effect.capability)
                SetupEffect.LaunchAppInfo -> context.openAppInfo()
                SetupEffect.Finished -> onFinished()
            }
        }
    }

    SetupScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
fun SetupScreen(state: SetupState, onIntent: (SetupIntent) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = MaxContentWidth)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Header(state = state, onBack = onBack)
            }
            state.sections.forEach { section ->
                item(key = "header-${section.section.name}") {
                    SectionHeader(section.section.title, subtitle = section.section.subtitle)
                }
                item(key = "group-${section.section.name}") {
                    SettingsGroup {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 2.dp)
                            CapabilityRow(item = item, onGrant = { onIntent(SetupIntent.Grant(item.capability.id)) })
                        }
                    }
                }
                if (section.section == CapabilitySection.Essentials) {
                    item(key = "restricted-hint") {
                        RestrictedSettingsHint(onOpenAppInfo = { onIntent(SetupIntent.OpenAppInfo) })
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { onIntent(SetupIntent.Finish) },
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .height(52.dp),
                ) {
                    Text(if (state.firstRun) "Continue" else "Done")
                }
                if (state.firstRun) {
                    Text(
                        "You can change all of this later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: SetupState, onBack: () -> Unit) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        if (!state.firstRun) {
            IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.height(32.dp))
        }
        Text(
            if (state.firstRun) "Let your AI use this phone" else "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            "Turn on only what you're comfortable with. Clients see a tool only after you allow what it needs.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        if (state.totalCount > 0) {
            val progress by animateFloatAsState(state.grantedCount / state.totalCount.toFloat(), label = "progress")
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${state.grantedCount} of ${state.totalCount} allowed",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.readyTools} tools ready",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(6.dp),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(item: CapabilityItem, onGrant: () -> Unit) {
    val capability = item.capability
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(
                    if (item.granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.capability(capability.id),
                contentDescription = null,
                tint = if (item.granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(capability.title, style = MaterialTheme.typography.titleMedium)
            Text(
                capability.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.toolCount > 0) {
                Text(
                    if (item.toolCount == 1) "Unlocks 1 tool" else "Unlocks ${item.toolCount} tools",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = item.granted,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
            label = "grant",
        ) { granted ->
            if (granted) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { role = Role.Image },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "${capability.title} allowed",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                FilledTonalButton(onClick = onGrant, contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun RestrictedSettingsHint(onOpenAppInfo: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "If Android shows \"Restricted setting\" for screen control or notification access, open App info, " +
                        "tap the menu (top right) and choose \"Allow restricted settings\". Android does this for apps " +
                        "installed outside the Play Store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenAppInfo, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Open App info")
                }
            }
        }
    }
}

/** Ids listed first in Setup, used by Home to nudge when they're missing. */
val EssentialCapabilityIds = listOf(Capabilities.NOTIFICATIONS, Capabilities.SCREEN_CONTROL, Capabilities.BACKGROUND)
