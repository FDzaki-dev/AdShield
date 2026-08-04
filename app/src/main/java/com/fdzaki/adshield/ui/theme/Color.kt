package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Matte Graphite / Jade Signal" — v3.0.0 visual identity.
 *
 * Direction: near-black matte graphite (never pure #000, always a hint of
 * cool-green undertone so it reads as "instrument," not "OLED battery
 * saver"), a single restrained jade signal color instead of a neon
 * acid-green, and definition via hairline strokes rather than drop shadows —
 * that's what reads as "matte premium" instead of "flat Material default."
 * Kept the original constant names so every existing screen (Rules,
 * Whitelist, Logs, Diagnostics, Onboarding) re-skins automatically without
 * being touched file-by-file; added a few new tokens for elevation/outline
 * that HomeScreen's redesign uses directly.
 */

// --- Elevation ladder (matte: tonal steps, not shadows) ---
val ShieldBgDark = Color(0xFF0A0C0B)      // base background
val ShieldSurface = Color(0xFF14170F)     // elevation 1 — standard cards
val ShieldSurface2 = Color(0xFF1B1F17)    // elevation 2 — nested/active cards
val ShieldSurfaceAlt = Color(0xFF1B1F17)  // legacy alias, kept for old references
val ShieldSurface3 = Color(0xFF232A20)    // elevation 3 — pressed/highlight state

// --- Signal (single restrained accent, not neon) ---
val ShieldGreen = Color(0xFF2FBE86)
val ShieldGreenDark = Color(0xFF1B5E42)
val ShieldAccentDim = Color(0xFF1E3F32)   // inactive ring track / dim fills

// --- Status ---
val ShieldDanger = Color(0xFFD9695F)
val ShieldWarning = Color(0xFFD6A756)

// --- Text ---
val ShieldWhite = Color(0xFFEDEFEA)
val ShieldTextMuted = Color(0xFF8B9690)
val ShieldTextFaint = Color(0xFF5C655F)

// --- Definition (hairline strokes instead of shadow elevation) ---
val ShieldOutline = Color(0xFF262C24)
val ShieldOutlineBright = Color(0xFF34C98E).copy(alpha = 0.35f)
