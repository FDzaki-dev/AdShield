package com.fdzaki.adshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * v3.0.0 — "Matte Graphite / Jade Signal" identity. Full colorScheme (not
 * just the 6 roles wired before) so Material3 default components — TopAppBar,
 * TabRow, OutlinedTextField, Switch, dialogs — that Rules/Whitelist/Logs/
 * Onboarding/Diagnostics already rely on inherit the new palette correctly
 * (e.g. `outline` now matters since several new HomeScreen cards use
 * hairline `outline`-colored borders instead of shadow elevation).
 */
private val AdShieldColorScheme = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = ShieldBgDark,
    primaryContainer = ShieldAccentDim,
    onPrimaryContainer = ShieldGreen,
    secondary = ShieldGreenDark,
    onSecondary = ShieldWhite,
    tertiary = ShieldWarning,
    onTertiary = ShieldBgDark,
    background = ShieldBgDark,
    onBackground = ShieldWhite,
    surface = ShieldSurface,
    onSurface = ShieldWhite,
    surfaceVariant = ShieldSurface2,
    onSurfaceVariant = ShieldTextMuted,
    surfaceContainer = ShieldSurface,
    surfaceContainerHigh = ShieldSurface2,
    surfaceContainerHighest = ShieldSurface3,
    outline = ShieldOutline,
    outlineVariant = ShieldOutline,
    error = ShieldDanger,
    onError = ShieldWhite
)

@Composable
fun AdShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdShieldColorScheme,
        typography = AdShieldTypography,
        shapes = AdShieldShapes,
        content = content
    )
}
