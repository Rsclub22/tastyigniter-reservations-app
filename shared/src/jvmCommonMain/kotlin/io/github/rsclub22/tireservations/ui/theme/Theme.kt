package io.github.rsclub22.tireservations.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFFC8472B)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0600),
    secondary = Color(0xFF775750),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF5F1504),
    primaryContainer = Color(0xFF7F2A15),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE7BDB4),
    background = Color(0xFF1A1110),
    surface = Color(0xFF1A1110),
)

/**
 * Die vom Betriebssystem vorgegebenen Farben, oder `null`, wenn es keine gibt.
 *
 * Auf Android ab 12 sind das die aus dem Hintergrundbild abgeleiteten Farben. Der
 * Desktop kennt so etwas nicht und liefert `null`, dann greifen [LightColors] und
 * [DarkColors].
 */
@Composable
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

@Composable
fun ReservationsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = dynamicColorScheme(dark)
        ?: if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
