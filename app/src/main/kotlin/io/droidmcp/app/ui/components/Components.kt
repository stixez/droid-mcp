package io.droidmcp.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.droidmcp.app.ui.theme.AppTheme

/**
 * Top bar + content for one screen. The bottom navigation bar is drawn by the app shell, so
 * screens don't add bottom insets of their own unless [handleBottomInset] is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Status bar is handled by the TopAppBar, navigation bar by the app shell.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // On tablets and in landscape, keep lines short enough to read: center a column.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize(),
            ) { content(padding) }
        }
    }
}

/** Widest a screen's content gets; wider windows get side margins instead of long lines. */
val MaxContentWidth = 720.dp

/** Small uppercase-free section label with optional subtitle. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rounded group container; rows inside are separated by thin gaps, like system Settings. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

/** Monospace text on a quiet background, scrolling sideways instead of wrapping. */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier, wrap: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text, style = AppTheme.code.medium, color = MaterialTheme.colorScheme.onSurface, softWrap = wrap)
    }
}

enum class BannerTone { Info, Attention, Error }

/** A one-line nudge with an optional action, e.g. "2 permissions missing · Review". */
@Composable
fun Banner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
) {
    val (container, content) = when (tone) {
        BannerTone.Info -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BannerTone.Attention -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BannerTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = container, contentColor = content) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            )
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) { Text(dismissLabel, color = content.copy(alpha = 0.75f)) }
            }
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content) }
            }
        }
    }
}

/** A status dot; [pulsing] adds a slow breathing halo for "live". */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false) {
    Box(modifier.size(14.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 2.2f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
                label = "scale",
            )
            val alpha by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
                label = "alpha",
            )
            Box(
                Modifier
                    .size(10.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(color, CircleShape),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
    }
}

/** A whole-row switch with title and explanation, like a system Settings row. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Thin gap between rows inside a [SettingsGroup]. */
@Composable
fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 2.dp)
}

/** Small rounded label, e.g. "Read-only" or "Asks first". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(modifier, shape = RoundedCornerShape(6.dp), color = color) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Horizontal row of tags with consistent spacing. */
@Composable
fun TagRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}
