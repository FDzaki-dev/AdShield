package com.fdzaki.adshield.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Radical + Literal Skeuomorphism — Dark Mode Only — Performance First"
 * theme identity, added as a user-selectable alternative to the default
 * AMOLED Glassmorphism identity (Color.kt) via the Settings screen's theme
 * picker (see SettingsScreen.kt / SettingsRepository.appTheme).
 *
 * Every constant below is copied 1:1 from
 * compose-skeuomorphism-radical-literal-dark-performance.md — this file is
 * the "single source of truth, centralize design tokens" requirement from
 * that spec's §20/§21 (Compose architecture / design token system). Do not
 * hand-edit a value here without updating it in the spec doc too.
 *
 * Dark mode only (spec §24) — there is intentionally no light variant.
 */

// ============================================================
// §2 — DARK-MODE COLOR FOUNDATION (verbatim hex values)
// ============================================================
val SkeuoBackground = Color(0xFF050505)
val SkeuoSurface = Color(0xFF101010)
val SkeuoSurfaceRaised = Color(0xFF171717)
val SkeuoSurfaceRecessed = Color(0xFF080808)

val SkeuoEdgeHighlight = Color.White.copy(alpha = 0.075f)
val SkeuoEdgeShadow = Color.Black.copy(alpha = 0.80f)

val SkeuoTextPrimary = Color(0xFFECECEC)
val SkeuoTextSecondary = Color(0xFFA6A6A6)
val SkeuoTextMuted = Color(0xFF707070)

val SkeuoAccent = Color(0xFF5F9EFF)

// ============================================================
// Semantic state colors (spec §18 — state must never rely on depth alone).
// Reuses the app's existing protected/danger/warning meaning, just kept as
// its own set here so this theme file has zero dependency on Color.kt.
// ============================================================
val SkeuoDanger = Color(0xFFFF453A)
val SkeuoWarning = Color(0xFFFF9F0A)
val SkeuoSuccess = Color(0xFF30D158)

// ============================================================
// §21 — DESIGN TOKEN SYSTEM
// ============================================================
data class SkeuoTokens(
    val raisedElevation: Dp = 6.dp,
    val pressedElevation: Dp = 1.dp,
    val highlightAlpha: Float = 0.075f,
    val shadowAlpha: Float = 0.80f,
    val cornerRadius: Dp = 14.dp,
    /** §6 example: `animateFloatAsState(targetValue = if (pressed) 0.975f else 1f)` */
    val pressedScale: Float = 0.975f
)

val LocalSkeuoTokens = SkeuoTokens()

// ============================================================
// §4/§5 — bevel/lighting helpers. Deliberately built from Shape + Brush +
// shadow()/border() only (spec §23 rendering priority: "Simple geometry >
// simple gradient > simple shadow" — no Canvas, no blur, no continuous
// animation), light source fixed top-left -> bottom-right everywhere.
// ============================================================

/** A raised physical surface: outer shadow (bottom/right-weighted via
 *  [shadow]'s default light source) + a top-left-highlight / bottom-right-
 *  shadow gradient border to read as beveled, per spec §5 "Raised object". */
fun Modifier.skeuoRaised(
    shape: Shape = RoundedCornerShape(LocalSkeuoTokens.cornerRadius),
    elevation: Dp = LocalSkeuoTokens.raisedElevation
): Modifier = this
    .shadow(elevation = elevation, shape = shape, clip = false)
    .background(
        Brush.linearGradient(
            colors = listOf(SkeuoSurfaceRaised, SkeuoSurface),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape
    )
    .border(
        BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(SkeuoEdgeHighlight, Color.Transparent, SkeuoEdgeShadow)
            )
        ),
        shape
    )

/** A recessed/sunken control: no outer shadow (it reads as pressed into the
 *  surface, not sitting above it), inverted bevel gradient (dark inner edge
 *  top-left, opposing highlight bottom-right) per spec §5 "Recessed object". */
fun Modifier.skeuoRecessed(
    shape: Shape = RoundedCornerShape(LocalSkeuoTokens.cornerRadius)
): Modifier = this
    .background(SkeuoSurfaceRecessed, shape)
    .border(
        BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(SkeuoEdgeShadow, Color.Transparent, SkeuoEdgeHighlight)
            )
        ),
        shape
    )
