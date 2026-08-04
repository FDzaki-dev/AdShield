package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Matte Graphite / Jade Signal" — v3.0.1 contrast pass.
 *
 * v3.0.0 shipped with elevation/text steps too close together (surface vs.
 * background, muted text vs. background) — looked "matte" on a color-picker
 * but read as low-legibility on an actual OLED panel (user report,
 * 2026-08-04, screenshot from device). This pass widens every step while
 * keeping the same restrained/matte direction — no neon, no gloss, just
 * enough separation to actually read text and card edges at a glance.
 * Rule of thumb applied here: body/caption text needs ~4.5:1 contrast
 * against background, card surfaces need to be visibly distinct from
 * background even without their border, and hairline borders need to
 * actually be visible, not just present in code.
 */

// --- Elevation ladder (matte: tonal steps, not shadows) ---
val ShieldBgDark = Color(0xFF0C0F0D)      // base background
val ShieldSurface = Color(0xFF1A1F1A)     // elevation 1 — standard cards (was too close to bg in v3.0.0)
val ShieldSurface2 = Color(0xFF262E25)    // elevation 2 — nested/active cards, icon chips
val ShieldSurfaceAlt = Color(0xFF262E25)  // legacy alias, kept for old references
val ShieldSurface3 = Color(0xFF313A2F)    // elevation 3 — pressed/highlight state

// --- Signal (single restrained accent, not neon) ---
val ShieldGreen = Color(0xFF3ED696)
val ShieldGreenDark = Color(0xFF1F6B4A)
val ShieldAccentDim = Color(0xFF224A3A)   // inactive ring track / dim fills

// --- Status ---
val ShieldDanger = Color(0xFFE1786F)
val ShieldWarning = Color(0xFFDDB264)

// --- Text ---
val ShieldWhite = Color(0xFFF3F5F1)
val ShieldTextMuted = Color(0xFFAEB9B0)   // was 0xFF8B9690 — too dark to read reliably
val ShieldTextFaint = Color(0xFF828D85)   // was 0xFF5C655F — captions were near-invisible

// --- Definition (hairline strokes instead of shadow elevation) ---
val ShieldOutline = Color(0xFF4A5346)     // was 0xFF262C24 — invisible against surfaces before
val ShieldOutlineBright = Color(0xFF3ED696).copy(alpha = 0.45f)
