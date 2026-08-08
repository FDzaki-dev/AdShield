package com.fdzaki.adshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.fdzaki.adshield.ui.theme.GlassBorder
import com.fdzaki.adshield.ui.theme.GlassSurfacePressed
import com.fdzaki.adshield.ui.theme.MidnightBlueAccent
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.TactileTokens

/**
 * Tactile slider (spec §7): visible recessed track, tactile knob with a restrained
 * radial gradient, active/inactive fill distinction, drag feedback, and a 44dp touch
 * target on the knob even though the visible knob is smaller — no tiny interaction area.
 */
@Composable
fun TactileSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    var widthPx by remember { mutableStateOf(0f) }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    fun updateFromX(x: Float) {
        if (widthPx <= 0f) return
        val f = (x / widthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp) // full touch target, even though visible track is thinner
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset -> updateFromX(offset.x) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> updateFromX(change.position.x) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Recessed track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color = GlassSurfacePressed, shape = RoundedCornerShape(3.dp))
                .border(width = TactileTokens.BorderHairline, color = GlassBorder, shape = RoundedCornerShape(3.dp))
        )
        // Active fill
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                .height(6.dp)
                .background(
                    brush = Brush.horizontalGradient(listOf(ShieldGreen.copy(alpha = 0.85f), ShieldGreen)),
                    shape = RoundedCornerShape(3.dp)
                )
        )
        // Tactile knob
        val knobOffsetDp = with(androidx.compose.ui.platform.LocalDensity.current) {
            (fraction * (widthPx - 24.dp.toPx())).toDp()
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset(x = knobOffsetDp)
                .shadow(elevation = TactileTokens.ElevationNormal, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(MidnightBlueAccent.copy(alpha = 0.9f), MidnightBlueAccent.copy(alpha = 0.35f)),
                        radius = 24f
                    ),
                    shape = CircleShape
                )
                .border(width = TactileTokens.BorderHairline, color = GlassBorder, shape = CircleShape)
        )
    }
}
