package com.fdzaki.adshield.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.fdzaki.adshield.ui.theme.PanelRecessedBase
import com.fdzaki.adshield.ui.theme.PanelRecessedHighlight
import com.fdzaki.adshield.ui.theme.ShieldGreen

/**
 * v4.4.0 — "Radikal Redesign" signature element (see PROJECT_STATE.md).
 * Replaces Material3 `Switch` at every toggle in the app (DNS/WARP mode
 * toggles, IPv6 routing, per-app whitelist, logging, auto-start). Reads as a
 * physical rocker switch: the TRACK is a recessed groove ([bevelBorderRecessed],
 * [TactileTokens.recessedBrush]) the switch sits IN, not a pill floating on
 * top of the panel — and the THUMB is a small raised disc with its own
 * shadow+bevel, following the same raised-panel depth language as every
 * other tactile control, just circular and small.
 *
 * API intentionally mirrors `Switch(checked, onCheckedChange, enabled)` so
 * existing call sites (which pass `onCheckedChange = null` and put an outer
 * `Modifier.toggleable` on the parent Row — see HomeScreen/LogsScreen/
 * WhitelistScreen) only need the composable name changed, not their logic.
 * [onCheckedChange] is accepted for API-shape parity and future direct-tap
 * use but is intentionally not wired to a click target here, matching every
 * current call site's existing toggleable-row pattern (a Switch with its own
 * separate hit target nested inside an already-toggleable Row is a known
 * a11y double-target trap — see the toggleable-row kdocs already in
 * HomeScreen/LogsScreen/WhitelistScreen for why that pattern was deliberately
 * removed).
 */
@Composable
fun TactileSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val thumbSize = 20.dp
    val thumbInset = 3.dp
    val trackShape = RoundedCornerShape(50)

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbInset else thumbInset,
        animationSpec = tween(140),
        label = "tactileSwitchThumb"
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(trackShape)
            .background(
                if (checked) {
                    Brush.verticalGradient(listOf(ShieldGreen.copy(alpha = 0.35f), PanelRecessedBase))
                } else {
                    TactileTokens.recessedBrush()
                }
            )
            .bevelBorderRecessed(trackShape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(thumbSize)
                .panelShadow(if (enabled) 3.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (checked) listOf(ShieldGreen, ShieldGreen.copy(alpha = 0.85f))
                        else listOf(PanelRecessedHighlight, PanelRecessedBase)
                    )
                )
                .bevelBorder(CircleShape)
        )
    }
}
