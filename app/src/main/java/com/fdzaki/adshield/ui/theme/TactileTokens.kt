package com.fdzaki.adshield.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Centralized tactile design tokens (spec §12 architecture rule: "do not duplicate
 * tactile constants throughout screen files"). Every TactileXxx component reads from
 * here. Single simulated light source: top-left → bottom-right (spec §3) — do not
 * introduce a component with a contradictory light direction.
 */
object TactileTokens {

    // --- Elevation (dp) — normal vs pressed/recessed (spec §5-§6) ---
    val ElevationNormal = 4.dp
    val ElevationPressed = 0.dp
    val ElevationRecessedRest = 1.dp // resting state for permanently-recessed controls (e.g. slider track)

    // --- Press micro-interaction (spec §6): immediate, no bounce, no huge scale ---
    const val PressScale = 0.98f
    const val PressAnimationDurationMs = 90

    // --- Corner radii reused across tactile controls (kept in sync with Shape.kt) ---
    val RadiusControl: Shape = RoundedCornerShape(14.dp)
    val RadiusSurface: Shape = RoundedCornerShape(20.dp)
    val RadiusPill: Shape = RoundedCornerShape(50)

    // --- Border widths ---
    val BorderHairline = 1.dp

    /**
     * Structural surface bevel (spec §4): AMOLED/glass dominant, Midnight Blue as a
     * restrained atmospheric tint — never a uniform blue fill.
     */
    fun surfaceGradient(): Brush = Brush.linearGradient(
        colors = listOf(
            GlassSurfaceElevated,
            MidnightBlueTint.copy(alpha = MidnightBlueGradientAlpha),
            GlassSurface
        )
    )

    /** Lifted tactile control (buttons/toggles/knobs at rest) — top-left highlight bias. */
    fun controlGradient(): Brush = Brush.linearGradient(
        colors = listOf(GlassSurfaceElevated, GlassSurface)
    )

    /** Pressed/recessed control — inverted weight, no highlight, reads as depressed. */
    fun controlPressedGradient(): Brush = Brush.linearGradient(
        colors = listOf(GlassSurfacePressed, GlassSurface)
    )

    /** Localized active-state glow fill (spec §9) — use sparingly, never on every card. */
    fun activeGlowGradient(accent: androidx.compose.ui.graphics.Color = MidnightBlueAccent): Brush =
        Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f))
        )
}
