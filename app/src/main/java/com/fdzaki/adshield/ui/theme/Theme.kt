package com.fdzaki.adshield.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.fdzaki.adshield.util.AppTheme

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

/**
 * "Radical + Literal Skeuomorphism — Dark Mode Only — Performance First"
 * color scheme — user-selectable alternative identity (Settings screen
 * theme picker). Tokens sourced verbatim from SkeuoTokens.kt / the design
 * spec doc §2. Kept as its own `darkColorScheme` (not a re-tint of
 * [AdShieldColorScheme]) so the two identities can diverge independently —
 * see PROJECT_STATE.md for why AdShieldColorScheme itself must not be
 * touched by this change.
 */
private val SkeuoColorScheme = darkColorScheme(
    primary = SkeuoAccent,
    onPrimary = SkeuoBackground,
    primaryContainer = SkeuoSurfaceRaised,
    onPrimaryContainer = SkeuoAccent,
    secondary = SkeuoAccent,
    onSecondary = SkeuoBackground,
    secondaryContainer = SkeuoSurfaceRaised,
    onSecondaryContainer = SkeuoTextPrimary,
    tertiary = SkeuoWarning,
    onTertiary = SkeuoBackground,
    background = SkeuoBackground,
    onBackground = SkeuoTextPrimary,
    surface = SkeuoSurface,
    onSurface = SkeuoTextPrimary,
    surfaceVariant = SkeuoSurfaceRaised,
    onSurfaceVariant = SkeuoTextSecondary,
    surfaceContainer = SkeuoSurface,
    surfaceContainerHigh = SkeuoSurfaceRaised,
    surfaceContainerHighest = SkeuoSurfaceRecessed,
    outline = SkeuoEdgeShadow,
    outlineVariant = SkeuoEdgeHighlight,
    error = SkeuoDanger,
    onError = SkeuoTextPrimary
)

/** Tighter, more "mechanical" corner-radius ladder than [AdShieldShapes] —
 *  spec §17: "not everything should be Level 3", literal hardware controls
 *  read as beveled rectangles/discs, not the default theme's soft 28-34dp
 *  "premium glass" radii. Uses [LocalSkeuoTokens.cornerRadius] (14dp) as the
 *  base unit so SkeuoButton and MaterialTheme shapes stay visually
 *  consistent without hardcoding the value twice. */
private val SkeuoShapes = Shapes(
    extraSmall = RoundedCornerShape(LocalSkeuoTokens.cornerRadius / 2),
    small = RoundedCornerShape(LocalSkeuoTokens.cornerRadius),
    medium = RoundedCornerShape(LocalSkeuoTokens.cornerRadius),
    large = RoundedCornerShape(LocalSkeuoTokens.cornerRadius * 1.4f),
    extraLarge = RoundedCornerShape(LocalSkeuoTokens.cornerRadius * 1.6f)
)

/**
 * @param themeMode one of [AppTheme] — read from SettingsRepository.appTheme
 * via MainViewModel, decided once per composition in MainActivity. Defaults
 * to [AppTheme.DEFAULT] so every existing call site (and any test/preview
 * that doesn't pass it) keeps rendering the original AMOLED identity
 * unchanged.
 */
@Composable
fun AdShieldTheme(themeMode: String = AppTheme.DEFAULT, content: @Composable () -> Unit) {
    val isSkeuo = themeMode == AppTheme.SKEUO_RADICAL_DARK
    MaterialTheme(
        colorScheme = if (isSkeuo) SkeuoColorScheme else AdShieldColorScheme,
        typography = AdShieldTypography,
        shapes = if (isSkeuo) SkeuoShapes else AdShieldShapes,
        content = content
    )
}
