package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v4.0.0 — "AMOLED Glassmorphism Hybrid + Midnight Blue Gradient" (Skeuomorphism-lite
 * Tactile UI). Full retint replacing the Apple-Style System Colors pass (v3.21.0).
 *
 * Source of truth: compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md
 * §2 "Suggested palette direction" — the constants below are copied verbatim from that
 * spec block, then every legacy `Shield*` name (still read by all 18 existing call sites)
 * is aliased onto them. Zero call sites needed editing — same reskin-at-the-source pattern
 * used since v3.0.0.
 *
 * Composition priority (spec §2.5): AMOLED black + frosted glass = dominant.
 * Midnight Blue = subtle atmospheric tint only. Never read as the primary background color.
 */

// ============================================================
// CANONICAL TOKENS — verbatim from the design spec §2 / §2.5
// ============================================================
val AmoledBackground = Color(0xFF030508)
val GlassSurface = Color(0xFF0A0F16)
val GlassSurfaceElevated = Color(0xFF101722)
val GlassSurfacePressed = Color(0xFF070B11)

// Midnight Blue is an ambient gradient layer, NOT the base identity.
val MidnightBlueTint = Color(0xFF191970)
val MidnightBlueAccent = Color(0xFF6670FF)

val TextPrimary = Color(0xFFEAF0F8)
val TextSecondary = Color(0xFFAAB5C4)

val GlassHighlight = Color.White.copy(alpha = 0.055f)
val GlassBorder = Color.White.copy(alpha = 0.035f)
val GlassShadow = Color.Black.copy(alpha = 0.70f)

const val MidnightBlueGradientAlpha = 0.08f

// ============================================================
// SEMANTIC STATE COLORS — app-specific (VPN protected/danger/warning).
// Not part of the decorative identity — used only as localized state/glow
// cues per spec §9-§10 (state must never rely on color alone; these pair
// with shape/position/icon changes at each call site).
// ============================================================
val ShieldGreen = Color(0xFF30D158)          // protected / connected signal
val ShieldGreenDark = Color(0xFF1A6E31)       // derived darker tint — Theme.kt `secondary`-role container only
val ShieldAccentDim = Color(0xFF10241A)       // low-chroma green-on-glass fill, tuned to sit on GlassSurface
val ShieldDanger = Color(0xFFFF453A)
val ShieldWarning = Color(0xFFFF9F0A)

// ============================================================
// LEGACY ALIASES — every screen/component still imports these 13 names
// unchanged; only their underlying values move to the new AMOLED+Glass+
// Midnight-Blue system. Do not delete: this is the whole point of the
// zero-call-site-edit reskin.
// ============================================================
val ShieldBgDark = AmoledBackground              // root/app background
val ShieldSurface = GlassSurface                 // main panels
val ShieldSurface2 = GlassSurfaceElevated         // secondary/nested panels, icon chips
val ShieldSurfaceAlt = GlassSurfaceElevated       // legacy alias, kept for old references
val ShieldSurface3 = GlassSurfacePressed          // pressed/recessed control surface

val ShieldWhite = TextPrimary                     // primary text
val ShieldTextMuted = TextSecondary               // secondary/supporting text
val ShieldTextFaint = TextSecondary.copy(alpha = 0.55f) // tertiary text — still ≥4.5:1 on GlassSurface

val ShieldOutline = GlassBorder                   // hairline edge — low-alpha, never bright white
val ShieldOutlineBright = ShieldGreen.copy(alpha = 0.45f) // active/selected-state border (semantic, not decorative)
