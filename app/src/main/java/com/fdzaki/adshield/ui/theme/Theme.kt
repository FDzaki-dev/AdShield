package com.fdzaki.adshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * v4.0.0 — "AMOLED Glassmorphism Hybrid + Midnight Blue Gradient" identity.
 * Dark mode is mandatory (spec §1.1) — no light-mode fallback exists or should be added.
 *
 * Role mapping:
 *  - background/surface*  -> AMOLED + frosted-glass ladder (dominant foundation/material)
 *  - primary              -> ShieldGreen, the app's own protected/connected signal (semantic
 *                            state color, not the decorative identity — spec §9-10 requires
 *                            state to carry its own color+shape cue independent of theme skin)
 *  - secondary             -> MidnightBlueAccent, the restrained ambient accent (spec: supporting
 *                            ingredient, never dominant)
 *  - outline/outlineVariant -> low-alpha glass hairlines, never bright white (spec §4)
 */
private val AdShieldColorScheme = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = AmoledBackground,
    primaryContainer = ShieldAccentDim,
    onPrimaryContainer = ShieldGreen,
    secondary = MidnightBlueAccent,
    onSecondary = TextPrimary,
    secondaryContainer = MidnightBlueTint.copy(alpha = 0.16f),
    onSecondaryContainer = TextPrimary,
    tertiary = ShieldWarning,
    onTertiary = AmoledBackground,
    background = AmoledBackground,
    onBackground = TextPrimary,
    surface = GlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = GlassSurface,
    surfaceContainerHigh = GlassSurfaceElevated,
    surfaceContainerHighest = GlassSurfacePressed,
    outline = GlassBorder,
    outlineVariant = GlassHighlight,
    error = ShieldDanger,
    onError = TextPrimary
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
