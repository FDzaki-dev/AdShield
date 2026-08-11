package com.fdzaki.adshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.fdzaki.adshield.ui.theme.LocalTrimAccent
import com.fdzaki.adshield.ui.theme.ShieldGreen

/**
 * v4.4.0 — "Radikal Redesign" (see PROJECT_STATE.md). Drop-in replacement
 * for `Card(colors = CardDefaults.cardColors(containerColor = ShieldSurface),
 * border = BorderStroke(1.dp, ShieldOutline))`, the exact pattern repeated
 * across every screen's panels/cards. Reads as a machined panel raised off
 * the chassis: drop shadow underneath ([TactileTokens.panelShadow]) + a
 * top-light gradient fill ([TactileTokens.raisedBrush]) + a gradient bevel
 * border ([bevelBorder]) — three real, independent depth cues instead of one
 * flat hairline.
 *
 * v4.7.2 — the DEFAULT (non-[accentActive]) bevel border now reads
 * [LocalTrimAccent] instead of the old fixed neutral `BevelHighlight`/
 * `BevelShadow` gradient — every card's edge everywhere in the app now
 * carries a faint tint of whichever theme (Brass/Lapis Lazuli) is active,
 * not just the two icon-tint call sites from v4.7.0. Purely decorative
 * (edge color only, not fill) — [accentActive] still forces `ShieldGreen`
 * unconditionally, so selected/active-state cards (WarpModeCard when
 * connected) are completely unaffected by the theme toggle, per the
 * "state colors never follow decorative theme" rule (see Color.kt kdoc).
 *
 * [accentActive] swaps the bevel to a green-tinted gradient for the
 * selected/active state (was `border = BorderStroke(1.dp, ShieldGreen.copy(alpha
 * = 0.4f))` at the WarpModeCard call site) — same signal, now bevel-shaped.
 */
@Composable
fun TactileSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    accentActive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val trimAccent = LocalTrimAccent.current
    Column(
        modifier = modifier
            .panelShadow(TactileTokens.elevationRaised, shape)
            .clip(shape)
            .background(TactileTokens.raisedBrush())
            .bevelBorder(shape, accent = true, accentColor = if (accentActive) ShieldGreen else trimAccent),
        content = content
    )
}
