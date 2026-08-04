package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Matte Graphite / Jade Signal" — v3.1.0 warm-graphite pass.
 *
 * v3.0.1 fixed raw contrast ratios but kept v3.0.0's green-tinted
 * near-black base (#0C0F0D) — technically legible, but reads as "murky
 * dark" rather than "premium matte" (user direction, 2026-08-04: move
 * away from darkness-for-its-own-sake, toward the kind of matte you'd
 * see on expensive hardware — Pixel/Sony matte plastics, premium
 * consumer electronics — which lean on warm neutral graphite, not a
 * muddy near-black tint). Changes in this pass:
 * - Base moved off pure near-black to a warm neutral graphite (still a
 *   dark theme, just not a "black hole" — this alone reads as more
 *   deliberate/expensive than maximal darkness).
 *   Every elevation step re-derived from that new base, keeping the
 *   same widened spacing philosophy from v3.0.1 (no step closer than
 *   ~4-5% perceptual lightness to its neighbor).
 * - Jade accent desaturated slightly (less "neon mint", more "brushed
 *   metal jade") to match the warmer, less high-contrast-tech, more
 *   premium-object direction.
 * - Warning shifted toward a muted brass/gold rather than amber — gold
 *   accents read as expensive in a way pure amber does not.
 * - Text steps re-verified against the new (lighter) base: still ~4.5:1+
 *   for muted body text, ~3:1+ for faint caption text.
 */

// --- Elevation ladder (matte: tonal steps, not shadows) ---
val ShieldBgDark = Color(0xFF17181A)      // base background — warm neutral graphite, not near-black
val ShieldSurface = Color(0xFF212325)     // elevation 1 — standard cards
val ShieldSurface2 = Color(0xFF2C2F30)    // elevation 2 — nested/active cards, icon chips
val ShieldSurfaceAlt = Color(0xFF2C2F30)  // legacy alias, kept for old references
val ShieldSurface3 = Color(0xFF3A3D3D)    // elevation 3 — pressed/highlight state

// --- Signal (single restrained accent, not neon) ---
val ShieldGreen = Color(0xFF3FC993)
val ShieldGreenDark = Color(0xFF23694C)
val ShieldAccentDim = Color(0xFF2B4038)   // inactive ring track / dim fills

// --- Status ---
val ShieldDanger = Color(0xFFE2847A)
val ShieldWarning = Color(0xFFD3AD6E)     // muted brass/gold, not straight amber

// --- Text ---
val ShieldWhite = Color(0xFFF6F5F2)       // warm off-white, not clinical white
val ShieldTextMuted = Color(0xFFBFC4C0)
val ShieldTextFaint = Color(0xFF93988F)

// --- Definition (hairline strokes instead of shadow elevation) ---
val ShieldOutline = Color(0xFF52564F)
val ShieldOutlineBright = Color(0xFF3FC993).copy(alpha = 0.45f)
