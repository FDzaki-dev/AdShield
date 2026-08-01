package com.fdzaki.adshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AdShieldColorScheme = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = ShieldBgDark,
    secondary = ShieldGreenDark,
    background = ShieldBgDark,
    surface = ShieldSurface,
    surfaceVariant = ShieldSurfaceAlt,
    onBackground = ShieldWhite,
    onSurface = ShieldWhite,
    error = ShieldDanger
)

@Composable
fun AdShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdShieldColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
