package com.fdzaki.adshield.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.theme.LocalSkeuoTokens
import com.fdzaki.adshield.ui.theme.SkeuoAccent
import com.fdzaki.adshield.ui.theme.SkeuoTextMuted
import com.fdzaki.adshield.ui.theme.SkeuoTextPrimary
import com.fdzaki.adshield.ui.theme.SkeuoTextSecondary
import com.fdzaki.adshield.ui.theme.skeuoRaised
import com.fdzaki.adshield.ui.theme.skeuoRecessed

/**
 * A literal physical button (spec §6/§18): raised + beveled by default,
 * compresses (scale down, elevation drops, surface reads as recessed) while
 * pressed, and gains an accent edge + slightly stronger surface when
 * [selected] — spec §18 SELECTED = "Raised + accent". Used by
 * SettingsScreen's theme picker; generic enough to reuse elsewhere.
 *
 * Animation is short and purposeful only (spec §14) — a single scale +
 * elevation tween on press/release, nothing perpetual or decorative.
 */
@Composable
fun SkeuoButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false
) {
    val tokens = LocalSkeuoTokens
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) tokens.pressedScale else 1f,
        label = "skeuoButtonScale"
    )

    val shape = RoundedCornerShape(tokens.cornerRadius)
    val physicalModifier = if (pressed) Modifier.skeuoRecessed(shape) else Modifier.skeuoRaised(shape)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(physicalModifier)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(PaddingValues(horizontal = 18.dp, vertical = 14.dp))
    ) {
        // Icon/title tint is the only thing that changes with [selected]
        // (spec §19: state must never rely on depth/scale alone) — the
        // beveled surface from skeuoRaised()/skeuoRecessed() above is
        // shared by both states so it stays a single reusable primitive.
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) SkeuoAccent else SkeuoTextSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(title, color = SkeuoTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (subtitle != null) {
            Text(subtitle, color = SkeuoTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
