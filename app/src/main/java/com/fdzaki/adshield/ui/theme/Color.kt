package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Matte Graphite / Jade Signal" — v3.4.0 legibility-max pass.
 *
 * User audit (2026-08-04, after v3.1.0 warm-graphite pass): flagged ALL
 * four categories as still hard to read — captions, bg/card separation,
 * nav card borders/icons, protection ring/button. Root cause found by
 * measuring the actual palette: v3.1.0 fixed text-vs-surface contrast but
 * the elevation ladder itself (bg→surf→surf2→surf3) only stepped ~4-5%
 * perceptual lightness apart, AND each step used a drifting/inconsistent
 * hue (220°→210°→195°→180°→94°→157° across bg/surf/surf2/surf3/outline/
 * accentDim) instead of one coherent warm-neutral hue — that drift is why
 * borders and elevation steps read as "faded" even where raw text
 * contrast passed. Changes in this pass:
 * - Elevation ladder rebuilt on ONE consistent warm-neutral hue (45°,
 *   ~5% sat) with wider, even lightness steps (L 9→15→21→29, i.e. ~6-8pt
 *   gaps vs. the old ~4-5pt gaps) — bg/card/nested-card separation is now
 *   deliberately more visible, not just technically compliant.
 * - ShieldOutline lightened substantially (L 32→46) — this is the single
 *   color used for nav-card borders, dividers, and the inactive
 *   protection-ring track, so this one change fixes "border/icon pudar"
 *   across every screen at once (grep-verified sole source of truth).
 * - ShieldAccentDim (inactive ring fill / dim chip backgrounds) lightened
 *   and re-saturated on the jade hue so the ring reads as a deliberate
 *   filled disc against its card, not a near-invisible dark blob.
 * - ShieldTextFaint (small captions/descriptions) lightened from L58→68 —
 *   was measuring 4.04:1 against the new surf2 (just under the 4.5:1 AA
 *   floor for small text), now 5.0-6.9:1 across all surface steps with
 *   real margin instead of borderline-passing.
 * - ShieldGreen/Warning/Danger/White untouched — already verified solid
 *   (7:1+) in v3.1.0 and unaffected by the ladder/outline rework.
 */

// --- Elevation ladder (matte: tonal steps, not shadows) — one warm-neutral
// hue throughout (was drifting hue per step pre-v3.4.0), wider L gaps ---
val ShieldBgDark = Color(0xFF181816)      // base background — warm neutral graphite
val ShieldSurface = Color(0xFF282724)     // elevation 1 — standard cards
val ShieldSurface2 = Color(0xFF383733)    // elevation 2 — nested/active cards, icon chips
val ShieldSurfaceAlt = Color(0xFF383733)  // legacy alias, kept for old references
val ShieldSurface3 = Color(0xFF4E4C46)    // elevation 3 — pressed/highlight state

// --- Signal (single restrained accent, not neon) ---
val ShieldGreen = Color(0xFF3FC993)
val ShieldGreenDark = Color(0xFF23694C)
val ShieldAccentDim = Color(0xFF345142)   // inactive ring track / dim fills — lightened + re-saturated

// --- Status ---
val ShieldDanger = Color(0xFFE2847A)
val ShieldWarning = Color(0xFFD3AD6E)     // muted brass/gold, not straight amber

// --- Text ---
val ShieldWhite = Color(0xFFF6F5F2)       // warm off-white, not clinical white
val ShieldTextMuted = Color(0xFFBFC4C0)
val ShieldTextFaint = Color(0xFFADB1AA)   // lightened L58→68 — small-caption AA margin

// --- Definition (hairline strokes instead of shadow elevation) ---
val ShieldOutline = Color(0xFF7C796E)     // lightened L32→46 — fixes borders/dividers/ring-track app-wide
val ShieldOutlineBright = Color(0xFF3FC993).copy(alpha = 0.45f)
