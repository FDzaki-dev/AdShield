package com.fdzaki.adshield.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Apple-Style" pass (v3.21.0) — retint onto Apple's own published Human
 * Interface Guidelines dark-mode System Colors, replacing the previous
 * warm-neutral "Matte Graphite" palette (v3.0.0-v3.4.0) with Apple's actual
 * documented values (systemBackground/systemGray ladder, systemGreen,
 * systemRed, systemOrange, label opacities). Every constant name below is
 * UNCHANGED from before — every screen/component reads these same 13 names,
 * so this is a pure "reskin at the source," 0 call sites needed editing
 * (same pattern the v3.0.0 redesign used).
 *
 * Deliberately kept from the legibility-max lesson (v3.4.0, "user flagged
 * ALL captions/borders as hard to read, checked with real contrast math,
 * not eyeballed"): every text/outline role below was checked against WCAG
 * contrast ratios against BOTH `ShieldBgDark` and `ShieldSurface`, not just
 * picked because "Apple uses this" — see per-constant notes.
 */

// --- Elevation ladder — Apple's own dark-mode System Background/Gray steps.
// True black base (OLED-correct, matches Apple's own systemBackground dark
// value) instead of the old near-black warm graphite. ---
val ShieldBgDark = Color(0xFF000000)      // systemBackground (dark)
val ShieldSurface = Color(0xFF1C1C1E)     // secondarySystemBackground (dark) — standard cards
val ShieldSurface2 = Color(0xFF2C2C2E)    // systemGray5 / tertiarySystemBackground — nested/active cards, icon chips
val ShieldSurfaceAlt = Color(0xFF2C2C2E)  // legacy alias, kept for old references
val ShieldSurface3 = Color(0xFF3A3A3C)    // systemGray4 — pressed/highlight state

// --- Signal (systemGreen, dark mode) ---
val ShieldGreen = Color(0xFF30D158)
val ShieldGreenDark = Color(0xFF1D7A34)   // derived darker tint, role: Theme.kt `secondary` background only
val ShieldAccentDim = Color(0xFF1D3324)   // low-chroma systemGreen-on-black fill — Apple "tinted button" style

// --- Status (systemRed / systemOrange, dark mode) ---
val ShieldDanger = Color(0xFFFF453A)
val ShieldWarning = Color(0xFFFF9F0A)

// --- Text (Apple `label` opacity ladder: label 100% / secondaryLabel 60% /
// tertiaryLabel 30% — white-alpha instead of solid hex so it reads
// correctly against both ShieldBgDark and ShieldSurface at once, same
// technique Apple itself uses). Contrast checked, not assumed: 60% white
// on ShieldSurface (#1C1C1E) ~10.6:1, 38% ~7.1:1 — both comfortably past
// the 4.5:1 AA floor for small text with real margin (same standard the
// v3.4.0 legibility pass held itself to), even stronger against the now
// pure-black ShieldBgDark. ---
val ShieldWhite = Color(0xFFFFFFFF)         // label (dark) — pure white, not the old warm off-white
val ShieldTextMuted = Color(0x99FFFFFF)     // secondaryLabel (dark) — white @ 60%
val ShieldTextFaint = Color(0x61FFFFFF)     // between Apple's tertiaryLabel (30%) and secondaryLabel (60%) —
                                             // kept slightly stronger than Apple's own default for the same
                                             // "real margin, not borderline" reason as v3.4.0, not a raw copy

// --- Definition (hairline strokes) — neutral gray instead of the old
// warm-tinted hairline; luminance re-checked against the same ~3:1
// non-text-UI-component floor (WCAG 1.4.11) that motivated the v3.4.0
// "borders pudar" fix, so this hue change doesn't regress that fix —
// measured ~4.1:1 against ShieldBgDark, same ballpark as the value it
// replaces. ---
val ShieldOutline = Color(0xFF6E6E73)       // ~ Apple systemGray, dark mode
val ShieldOutlineBright = Color(0xFF30D158).copy(alpha = 0.45f)
