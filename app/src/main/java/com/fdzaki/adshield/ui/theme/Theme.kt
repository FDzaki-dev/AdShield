package com.fdzaki.adshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * v4.4.0 — "Radikal Redesign": brushed-titanium instrument-panel identity,
 * replacing "AMOLED Glassmorphism Hybrid" (v3.43.0/v4.0.0). See Color.kt
 * kdoc + PROJECT_STATE.md for the full regression story and rationale.
 * Dark mode is still mandatory — no light-mode fallback exists or should be added.
 *
 * Role mapping:
 *  - background/surface*   -> Chassis/Panel ladder (the physical case + panels)
 *  - primary               -> ShieldGreen, protected/connected signal (semantic
 *                             state color, unchanged from every prior identity —
 *                             this is the one color users should always recognize)
 *  - secondary              -> AccentBrass, metal-trim accent (replaces Midnight Blue)
 *  - outline/outlineVariant -> bevel highlight/shadow tokens (see BevelHighlight/
 *                             BevelShadow in Color.kt) — real gradient-bevel edges,
 *                             not flat hairlines
 */
private val AdShieldColorScheme = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = ChassisBg,
    primaryContainer = ShieldAccentDim,
    onPrimaryContainer = ShieldGreen,
    secondary = AccentBrass,
    onSecondary = Ink,
    secondaryContainer = AccentBrass.copy(alpha = 0.18f),
    onSecondaryContainer = Ink,
    tertiary = ShieldWarning,
    onTertiary = ChassisBg,
    background = ChassisBg,
    onBackground = Ink,
    surface = PanelBase,
    onSurface = Ink,
    surfaceVariant = PanelHighlight,
    onSurfaceVariant = Slate,
    surfaceContainer = PanelBase,
    surfaceContainerHigh = PanelHighlight,
    surfaceContainerHighest = PanelRecessedBase,
    outline = BevelHighlight,
    outlineVariant = BevelShadow,
    error = ShieldDanger,
    onError = Ink
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
