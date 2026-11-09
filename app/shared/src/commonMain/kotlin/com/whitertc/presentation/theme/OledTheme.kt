package com.whitertc.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class OledColors(
    val background: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF000000),
    val cardBackground: Color = Color(0xFF000000),
    val surfaceElevated: Color = Color(0xFF0C0D0F),
    val borderDefault: Color = Color(0x1FFFFFFF),
    val borderHairline: Color = Color(0x1FFFFFFF),
    val borderActive: Color = Color(0x59E898A8),
    val borderSubtle: Color = Color(0x0FFFFFFF),
    val textPrimary: Color = Color(0xFFEDEDED),
    val textSecondary: Color = Color(0xFF9C9C9D),
    val textMuted: Color = Color(0xFF6B6E76),
    val emerald: Color = Color(0xFF00FF66),
    val emeraldDim: Color = Color(0x2200FF66),
    val amber: Color = Color(0xFFFFB700),
    val amberDim: Color = Color(0x22FFB700),
    val crimson: Color = Color(0xFFFF334B),
    val crimsonDim: Color = Color(0x22FF334B),
    // Primary Accent: Dusty Rose (easily switchable to Acid Coral #FF7582)
    val rose: Color = Color(0xFFE898A8),
    val roseDim: Color = Color(0x22E898A8),
    // Compatibility alias: cyan maps to our primary accent
    val cyan: Color = rose,
    val cyanDim: Color = roseDim,
    val violet: Color = Color(0xFFA78BFA),
    val violetDim: Color = Color(0x22A78BFA)
)

object OledTypography {
    val mono = FontFamily.Monospace
    val sans = FontFamily.Default
}

val LocalOledColors = staticCompositionLocalOf { OledColors() }

object OledTheme {
    val colors: OledColors
        @Composable
        get() = LocalOledColors.current

    val typography: OledTypography
        get() = OledTypography
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF141416),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF00E676),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFF9E9EA7),
    outline = Color(0xFF1E1E22),
    outlineVariant = Color(0xFF141416),
    error = Color(0xFFFF453A),
    onError = Color(0xFF000000)
)

@Composable
fun WhiteRtcTheme(
    oledColors: OledColors = OledColors(),
    content: @Composable () -> Unit
) {
    ApplySystemBarsStyle(isLightTheme = false)

    CompositionLocalProvider(
        LocalOledColors provides oledColors
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}

@Composable
fun WhiteProxyTheme(
    oledColors: OledColors = OledColors(),
    content: @Composable () -> Unit
) = WhiteRtcTheme(oledColors, content)

