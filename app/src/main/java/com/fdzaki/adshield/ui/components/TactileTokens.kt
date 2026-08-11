package com.fdzaki.adshield.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fdzaki.adshield.ui.theme.BevelHighlight
import com.fdzaki.adshield.ui.theme.BevelRecessedHighlight
import com.fdzaki.adshield.ui.theme.BevelRecessedShadow
import com.fdzaki.adshield.ui.theme.BevelShadow
import com.fdzaki.adshield.ui.theme.PanelHighlight
import com.fdzaki.adshield.ui.theme.PanelRecessedBase
import com.fdzaki.adshield.ui.theme.PanelRecessedHighlight
import com.fdzaki.adshield.ui.theme.PanelShadow

/**
 * v4.4.0 — "Radikal Redesign" shared depth primitives (see PROJECT_STATE.md
 * + Color.kt kdoc for the full regression story this batch fixes).
 *
 * These are real Compose light-simulation techniques, not decoration bolted
 * on after the fact ("tanpa akal-akalan" — the point of this batch is that
 * every surface below is ACTUALLY built and ACTUALLY used by a screen, unlike
 * the v3.43.0 Tactile* components that were built once and never wired in):
 *
 *  - [raisedBrush]/[recessedBrush]: a top->bottom (or inverted) gradient fill
 *    simulating a single light source hitting the panel from above — the
 *    single biggest contributor to "this looks like real hardware."
 *  - [Modifier.bevelBorder]: `Modifier.border(width, Brush, shape)` — a real
 *    Compose API, not a drawn-on image — with a light-to-dark gradient stop
 *    pair, giving a machined edge instead of a flat 1dp hairline.
 *  - [Modifier.panelShadow]: real elevation via `Modifier.shadow()`. Recessed
 *    surfaces intentionally get NO shadow (a groove doesn't cast one) — the
 *    inverted [bevelBorder] direction is what reads as "pressed in" instead.
 *
 * v4.7.3 — "efek timbul dimaksimalkan" (user request): [elevationRaised]
 * 6dp -> 14dp (more than doubled — every card/button now casts a visibly
 * deeper shadow) and [bevelWidth] 1dp -> 1.5dp (a thicker, more legible
 * machined edge). [elevationRaisedPressed] left untouched at 1dp — keeping
 * the *pressed* state shallow while the *rest* state got much taller is
 * what makes the press animation read as a dramatic collapse, not just a
 * uniformly bigger shadow everywhere. The actual highlight/shadow color
 * CONTRAST driving the embossed look lives in Color.kt (`PanelHighlight`/
 * `PanelShadow`/`BevelHighlight`/`BevelShadow` etc.) — see that file's kdoc
 * for the specific value changes.
 */
object TactileTokens {
    val elevationRaised: Dp = 14.dp
    val elevationRaisedPressed: Dp = 1.dp
    val bevelWidth: Dp = 1.5.dp

    fun raisedBrush(): Brush = Brush.verticalGradient(listOf(PanelHighlight, PanelShadow))
    fun raisedBrushPressed(): Brush = Brush.verticalGradient(listOf(PanelShadow, PanelHighlight))
    fun recessedBrush(): Brush = Brush.verticalGradient(listOf(PanelRecessedBase, PanelRecessedHighlight))

    fun bevelBrush(accent: Boolean = false, accentColor: androidx.compose.ui.graphics.Color? = null):
        Brush = if (accent && accentColor != null) {
        Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), accentColor.copy(alpha = 0.18f)))
    } else {
        Brush.verticalGradient(listOf(BevelHighlight, BevelShadow))
    }

    fun bevelBrushRecessed(): Brush = Brush.verticalGradient(listOf(BevelRecessedHighlight, BevelRecessedShadow))
}

fun Modifier.bevelBorder(shape: Shape, accent: Boolean = false, accentColor: androidx.compose.ui.graphics.Color? = null): Modifier =
    this.border(TactileTokens.bevelWidth, TactileTokens.bevelBrush(accent, accentColor), shape)

fun Modifier.bevelBorderRecessed(shape: Shape): Modifier =
    this.border(TactileTokens.bevelWidth, TactileTokens.bevelBrushRecessed(), shape)

fun Modifier.panelShadow(elevation: Dp, shape: Shape): Modifier =
    this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = androidx.compose.ui.graphics.Color.Black,
        spotColor = androidx.compose.ui.graphics.Color.Black
    )
