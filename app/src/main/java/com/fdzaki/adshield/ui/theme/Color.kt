package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v4.4.0 — "Radikal Redesign": full retint + REAL component rebuild, replacing
 * the flat "AMOLED Glassmorphism" identity (v3.43.0/v4.0.0). See PROJECT_STATE.md
 * for why this exists: the previous pass built genuine skeuomorphic ("Tactile")
 * components but never wired them into any screen, and the v4.0.0 cleanup then
 * deleted them as "0 call site" dead code — the setting/feel a user could
 * actually see never existed, hence "menghilang tanpa jejak" even though the
 * code briefly did.
 *
 * Direction: brushed-titanium instrument panel, not glass. AdShield's own
 * subject — a hardware-style shield/tunnel toggle — is a physical control
 * panel, not a frosted pane, so depth here comes from real light-simulation
 * (gradient fill + gradient bevel border + drop shadow, see TactileTokens.kt)
 * rather than translucency. This also directly fixes the readability
 * complaint: the old palette leaned on <10%-alpha hairlines and dimmed
 * secondary text for its "matte" look; every text/border token below is
 * measurably higher-contrast against its resting surface.
 *
 * Palette (named per spec convention — 6 anchors):
 *  Chassis   #17181B  — root background, the "case" the panels sit in
 *  Panel     #212327  — raised panel resting fill
 *  Ink       #F3F0EA  — primary text, warm off-white
 *  Slate     #C7C0B3  — secondary text (readability fix: was ~55%-alpha before)
 *  Signal    #30D158  — protected/connected state (unchanged — brand recognition)
 *  Brass     #C98A4B  — secondary/metal-trim accent, replaces the old Midnight Blue
 */

// ============================================================
// CHASSIS + PANEL — the physical-panel depth system.
// Panels are always rendered as a top->bottom gradient between
// PanelHighlight and PanelShadow (see TactileTokens.raisedBrush), never a
// flat fill — that gradient IS the "skeuomorphism-lite," not a texture bitmap.
// ============================================================
val ChassisBg = Color(0xFF17181B)
val PanelBase = Color(0xFF212327)
val PanelHighlight = Color(0xFF2C2F34)      // top-of-gradient stop, raised panels
val PanelShadow = Color(0xFF16171A)         // bottom-of-gradient stop, raised panels
val PanelRecessedBase = Color(0xFF121316)   // pressed/inset controls sit BELOW chassis level
val PanelRecessedHighlight = Color(0xFF1B1D21)

// Bevel edge — a real gradient border (Compose `Modifier.border(width, Brush, shape)`,
// not a drawn-on highlight image) from bright-top to dark-bottom simulates a light
// source hitting the top edge of a machined metal panel.
val BevelHighlight = Color.White.copy(alpha = 0.14f)
val BevelShadow = Color.Black.copy(alpha = 0.65f)
// Inverted bevel for recessed/pressed controls — light source now reads as coming
// from BELOW the groove, which is what actually sells "pressed in" vs "raised."
val BevelRecessedHighlight = Color.Black.copy(alpha = 0.55f)
val BevelRecessedShadow = Color.White.copy(alpha = 0.08f)

// ============================================================
// TEXT — readability fix. Slate replaces the old ~55%-alpha "faint" text with
// a real opaque warm-gray so it stays legible at small sizes on Panel/Chassis.
// ============================================================
val Ink = Color(0xFFF3F0EA)
val Slate = Color(0xFFC7C0B3)
val SlateDim = Color(0xFF8B8579)            // tertiary — still opaque, was alpha-based before

// ============================================================
// SIGNAL COLORS — app state, not decorative identity (protected/danger/warning).
// ============================================================
val ShieldGreen = Color(0xFF30D158)
val ShieldGreenDark = Color(0xFF1A6E31)
val ShieldAccentDim = Color(0xFF16311F)     // low-chroma green fill for active icon chips/insets
val ShieldDanger = Color(0xFFFF5449)
val ShieldWarning = Color(0xFFFFA733)
val AccentBrass = Color(0xFFC98A4B)         // metal-trim secondary accent

// ============================================================
// LEGACY ALIASES — every screen still imports these 13 names; only the
// values move. Do not delete: same zero-call-site-edit reskin pattern used
// since v3.0.0/v3.21.0/v3.43.0 — screens NOT yet retrofitted with the new
// Tactile* components (see PROJECT_STATE.md "wired vs pending" list) still
// render correctly, just without the bevel/gradient depth.
// ============================================================
val ShieldBgDark = ChassisBg
val ShieldSurface = PanelBase
val ShieldSurface2 = PanelHighlight
val ShieldSurfaceAlt = PanelHighlight
val ShieldSurface3 = PanelRecessedBase

val ShieldWhite = Ink
val ShieldTextMuted = Slate
val ShieldTextFaint = SlateDim

val ShieldOutline = BevelHighlight
val ShieldOutlineBright = ShieldGreen.copy(alpha = 0.45f)
