package io.github.rsclub22.tireservations.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Der Desktop kennt keine vom System vorgegebene Farbpalette. Damit greifen die
 * festen Schemata des Themes - dieselben, die Android vor Version 12 benutzt.
 */
@Composable
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? = null
