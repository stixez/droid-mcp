package io.droidmcp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.droidmcp.app.R

// A fixed palette rather than dynamic color: deep green for "on / reachable", amber for
// "needs you", neutrals with a slight green cast so the app reads as one piece.

private val Light = lightColorScheme(
    primary = Color(0xFF1E6B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EEDA),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE9DD),
    onSecondaryContainer = Color(0xFF082019),
    tertiary = Color(0xFF855400),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2A1700),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5F7F6),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF3F1),
    surfaceContainer = Color(0xFFE9EEEB),
    surfaceContainerHigh = Color(0xFFE3E8E5),
    surfaceContainerHighest = Color(0xFFDDE3E0),
    surfaceDim = Color(0xFFD5DBD8),
    surfaceBright = Color(0xFFF5F7F6),
    inverseSurface = Color(0xFF2C3230),
    inverseOnSurface = Color(0xFFECF2EF),
    inversePrimary = Color(0xFF8DD5BE),
    scrim = Color(0xFF000000),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD8BE),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFA0F3D8),
    secondary = Color(0xFFB1CCC1),
    onSecondary = Color(0xFF1D352D),
    secondaryContainer = Color(0xFF334B43),
    onSecondaryContainer = Color(0xFFCDE9DD),
    tertiary = Color(0xFFFFB866),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1311),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1311),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFF090E0C),
    surfaceContainerLow = Color(0xFF161C1A),
    surfaceContainer = Color(0xFF1A201E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    surfaceDim = Color(0xFF0E1311),
    surfaceBright = Color(0xFF343A37),
    inverseSurface = Color(0xFFDEE4E1),
    inverseOnSurface = Color(0xFF2C3230),
    inversePrimary = Color(0xFF1E6B58),
    scrim = Color(0xFF000000),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** Monospace styles for technical text: tool names, addresses, tokens, commands. */
@Immutable
data class CodeTypography(
    val small: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 18.sp),
    val medium: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 20.sp),
    val label: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

private val LocalCodeTypography = staticCompositionLocalOf { CodeTypography() }

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Medium),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun DroidMcpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

/** Access to the app's extra theme values: `AppTheme.code.medium`. */
object AppTheme {
    val code: CodeTypography
        @Composable get() = LocalCodeTypography.current
}
