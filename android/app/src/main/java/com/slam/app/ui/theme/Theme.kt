package com.slam.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slam.app.data.AppearanceMode
import com.slam.app.data.UiPreferences

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
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val preference by UiPreferences(context).appearance.collectAsStateWithLifecycle(
        initialValue = AppearanceMode.LIGHT,
    )
    val useDark = darkTheme ?: (preference == AppearanceMode.DARK)
    val scheme = if (useDark) DarkScheme else LightScheme
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !useDark
            isAppearanceLightNavigationBars = !useDark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = SlamTypography,
        shapes = SlamShapes,
        content = content,
    )
}
