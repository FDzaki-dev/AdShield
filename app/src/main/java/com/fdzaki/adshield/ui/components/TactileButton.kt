package com.fdzaki.adshield.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.fdzaki.adshield.ui.theme.GlassBorder
import com.fdzaki.adshield.ui.theme.ShieldOutlineBright
import com.fdzaki.adshield.ui.theme.TactileTokens
import com.fdzaki.adshield.ui.theme.TextPrimary

/**
 * Tactile button (spec §6-7): sinks on press (scale + elevation drop), Midnight-Blue
 * glass bevel surface, restrained top-left highlight via the surface gradient. Active/
 * selected state adds both a structural change (glow ring) AND a color change — never
 * color alone (spec §10 accessibility rule).
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    contentColor: Color = TextPrimary,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(
        targetValue = if (!enabled) TactileTokens.ElevationPressed
        else if (isPressed) TactileTokens.ElevationPressed else TactileTokens.ElevationNormal,
        animationSpec = tween(TactileTokens.PressAnimationDurationMs),
        label = "tactileElevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) TactileTokens.PressScale else 1f,
        animationSpec = tween(TactileTokens.PressAnimationDurationMs),
        label = "tactileScale"
    )

    val borderColor = if (active) ShieldOutlineBright else GlassBorder
    val alphaOut = if (enabled) 1f else 0.45f

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = elevation, shape = TactileTokens.RadiusControl, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(TactileTokens.RadiusControl)
            .background(brush = if (isPressed) TactileTokens.controlPressedGradient() else TactileTokens.controlGradient())
            .border(width = TactileTokens.BorderHairline, color = borderColor, shape = TactileTokens.RadiusControl)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContentColor provides contentColor.copy(alpha = alphaOut)
        ) {
            content()
        }
    }
}
