package com.fdzaki.adshield.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fdzaki.adshield.ui.theme.GlassBorder
import com.fdzaki.adshield.ui.theme.GlassSurfacePressed
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldOutlineBright
import com.fdzaki.adshield.ui.theme.TactileTokens

/**
 * Tactile switch (spec §7): OFF = recessed/muted, ON = active + subtly illuminated
 * (structural change via thumb position/elevation AND color change via track tint —
 * satisfies §10's "state must not depend solely on structural changes"),
 * PRESSED = temporarily deeper, DISABLED = reduced contrast.
 */
@Composable
fun TactileSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val trackColor = when {
        !enabled -> GlassSurfacePressed.copy(alpha = 0.5f)
        checked -> ShieldGreen.copy(alpha = 0.28f)
        else -> GlassSurfacePressed
    }
    val borderColor = if (checked) ShieldOutlineBright else GlassBorder
    val thumbColor = if (checked) ShieldGreen else Color(0xFF3A4250)

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(TactileTokens.PressAnimationDurationMs),
        label = "switchThumbOffset"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) TactileTokens.PressScale else 1f,
        animationSpec = tween(TactileTokens.PressAnimationDurationMs),
        label = "switchThumbScale"
    )
    val thumbElevation by animateDpAsState(
        targetValue = if (isPressed || !checked) TactileTokens.ElevationPressed else TactileTokens.ElevationNormal,
        animationSpec = tween(TactileTokens.PressAnimationDurationMs),
        label = "switchThumbElevation"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .background(color = trackColor, shape = CircleShape)
            .border(width = TactileTokens.BorderHairline, color = borderColor, shape = CircleShape)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(2.dp),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .scale(thumbScale)
                .shadow(elevation = thumbElevation, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(color = thumbColor.copy(alpha = if (enabled) 1f else 0.45f), shape = CircleShape)
        )
    }
}
