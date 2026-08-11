package com.fdzaki.adshield.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * v4.7.0 — "custom theme #2" toggle. Two decorative identities, both on the
 * same titanium instrument-panel base (Chassis/Panel/Bevel tokens never
 * change — see Color.kt kdoc): only the trim accent swaps.
 *
 *  - TITANIUM_BRASS — default, [AccentBrass]. The original v4.4.0 identity.
 *  - TITANIUM_LAPIS — new, [LapisLazuli]. See Color.kt "THEME 2" section.
 *
 * `Preview`/`display` are user-facing Indonesian strings for the settings
 * toggle row — kept here (not in the screen file) so the enum and its
 * label can never drift apart across the two places that reference it.
 */
enum class AppThemeVariant(val storageKey: String, val displayName: String) {
    TITANIUM_BRASS("titanium_brass", "Titanium + Brass"),
    TITANIUM_LAPIS("titanium_lapis", "Titanium + Lapis Lazuli");

    companion object {
        fun fromStorageKey(key: String): AppThemeVariant =
            entries.find { it.storageKey == key } ?: TITANIUM_BRASS
    }
}

fun AppThemeVariant.trimAccent(): Color = when (this) {
    AppThemeVariant.TITANIUM_BRASS -> AccentBrass
    AppThemeVariant.TITANIUM_LAPIS -> LapisLazuli
}

fun AppThemeVariant.trimAccentDim(): Color = when (this) {
    AppThemeVariant.TITANIUM_BRASS -> AccentBrass.copy(alpha = 0.18f)
    AppThemeVariant.TITANIUM_LAPIS -> LapisLazuliDim
}

/**
 * Runtime-swappable decorative accent. Compile-time `val AccentBrass`/
 * `val LapisLazuli` alone can't drive a user toggle — components that want
 * to follow the current theme (TactileButton Primary fill, NavRow icon
 * chips) read [LocalTrimAccent.current] instead of importing a fixed color,
 * the same way Material3 components read `MaterialTheme.colorScheme`.
 * Default value here is only the "no provider found" fallback (should never
 * actually apply — [AdShieldTheme] always provides it); kept as
 * TITANIUM_BRASS's accent so a missed-provider bug still renders *something*
 * sane rather than an obviously-broken color.
 */
val LocalTrimAccent = staticCompositionLocalOf { AccentBrass }
