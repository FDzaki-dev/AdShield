package com.fdzaki.adshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * v4.4.0 — "Radikal Redesign": brushed-titanium instrument-panel identity,
 * replacing "AMOLED Glassmorphism Hybrid" (v3.43.0/v4.0.0). See Color.kt
 * kdoc + PROJECT_STATE.md for the full regression story and rationale.
 * Dark mode is still mandatory — no light-mode fallback exists or should be added.
 *
 * v4.7.0 — [themeVariant] param added: the panel/chassis/bevel/state-color
 * ladder below (background, surface family, primary, error) is IDENTICAL for both
 * [AppThemeVariant]s, only `secondary`/`secondaryContainer` and
 * [LocalTrimAccent] (what decorative components actually read — see
 * ThemeVariant.kt kdoc for why the Material3 colorScheme fields alone
 * weren't enough) move. Default parameter value keeps every existing
 * `AdShieldTheme { ... }` call site (there's exactly one, MainActivity)
 * compiling unchanged until it's wired to the live setting.
 *
 * Role mapping:
 *  - background/surface*   -> Chassis/Panel ladder (the physical case + panels)
 *  - primary               -> ShieldGreen, protected/connected signal (semantic
 *                             state color, unchanged from every prior identity —
 *                             this is the one color users should always recognize,
 *                             and does NOT change with [themeVariant])
 *  - secondary              -> [AppThemeVariant.trimAccent], decorative
 *                             metal-trim accent (Brass or Lapis Lazuli)
 *  - outline/outlineVariant -> bevel highlight/shadow tokens (see BevelHighlight/
 *                             BevelShadow in Color.kt) — real gradient-bevel edges,
 *                             not flat hairlines
 */
private fun colorSchemeFor(themeVariant: AppThemeVariant) = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = ChassisBg,
    primaryContainer = ShieldAccentDim,
    onPrimaryContainer = ShieldGreen,
    secondary = themeVariant.trimAccent(),
    onSecondary = Ink,
    secondaryContainer = themeVariant.trimAccentDim(),
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
fun AdShieldTheme(
    themeVariant: AppThemeVariant = AppThemeVariant.TITANIUM_BRASS,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTrimAccent provides themeVariant.trimAccent()) {
        MaterialTheme(
            colorScheme = colorSchemeFor(themeVariant),
            typography = AdShieldTypography,
            shapes = AdShieldShapes,
            content = content
        )
    }
}
