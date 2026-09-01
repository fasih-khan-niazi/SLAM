package com.slam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = SlamDark.Primary,
    onPrimary = SlamDark.Background,
    secondary = SlamDark.Accent,
    background = SlamDark.Background,
    surface = SlamDark.Surface,
    surfaceVariant = SlamDark.SurfaceRaised,
    onBackground = SlamDark.Text,
    onSurface = SlamDark.Text,
    onSurfaceVariant = SlamDark.TextMuted,
    error = SlamDark.Danger,
    outline = SlamDark.Border,
)

private val LightScheme = lightColorScheme(
    primary = SlamLight.Primary,
    onPrimary = SlamLight.Surface,
    secondary = SlamLight.Accent,
    background = SlamLight.Background,
    surface = SlamLight.Surface,
    surfaceVariant = SlamLight.SurfaceRaised,
    onBackground = SlamLight.Text,
    onSurface = SlamLight.Text,
    onSurfaceVariant = SlamLight.TextMuted,
    error = SlamLight.Danger,
    outline = SlamLight.Border,
)

val SlamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SlamTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = SlamTypography,
        shapes = SlamShapes,
        content = content,
    )
}

@Composable
fun prefersDark(forceDark: Boolean = true): Boolean {
    return if (forceDark) true else isSystemInDarkTheme()
}
