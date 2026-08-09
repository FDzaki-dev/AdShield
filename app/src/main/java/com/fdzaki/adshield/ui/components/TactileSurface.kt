package com.fdzaki.adshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
    Column(
        modifier = modifier
            .panelShadow(TactileTokens.elevationRaised, shape)
            .clip(shape)
            .background(TactileTokens.raisedBrush())
            .bevelBorder(shape, accent = accentActive, accentColor = ShieldGreen),
        content = content
    )
}
