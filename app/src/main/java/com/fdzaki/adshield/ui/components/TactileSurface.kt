package com.fdzaki.adshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import com.fdzaki.adshield.ui.theme.GlassBorder
import com.fdzaki.adshield.ui.theme.TactileTokens

/**
 * Quiet structural container (spec §8: "structural containers should remain visually
 * quieter than interactive physical controls"). Uses the restrained AMOLED+glass+
 * Midnight-Blue bevel, never the stronger tactile treatment reserved for controls.
 *
 * No press state, no glow — this is the backdrop tactile controls sit on top of.
 */
@Composable
fun TactileSurface(
    modifier: Modifier = Modifier,
    shape: Shape = TactileTokens.RadiusSurface,
    elevation: androidx.compose.ui.unit.Dp = TactileTokens.ElevationRecessedRest,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, ambientColor = androidx.compose.ui.graphics.Color.Black, spotColor = androidx.compose.ui.graphics.Color.Black)
            .clip(shape)
            .background(brush = TactileTokens.surfaceGradient())
            .border(width = TactileTokens.BorderHairline, color = GlassBorder, shape = shape)
    ) {
        content()
    }
}
