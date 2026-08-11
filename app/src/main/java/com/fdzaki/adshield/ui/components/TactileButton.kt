package com.fdzaki.adshield.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.fdzaki.adshield.ui.theme.Ink
import com.fdzaki.adshield.ui.theme.ShieldTextMuted

/**
 * v4.4.0 — "Radikal Redesign" (see PROJECT_STATE.md). A physically-pressable
 * raised button: shadow + top-light gradient fill at rest, both collapse
 * toward flat ([TactileTokens.elevationRaisedPressed], [TactileTokens.raisedBrushPressed])
 * for the duration of the press, then animate back — the button visibly sinks
 * into the panel on tap and pops back up on release, not just a ripple overlay.
 *
 * [TactileButtonVariant.Primary] = filled trim-accent surface (see
 * ui/theme/ThemeVariant.kt — [LocalTrimAccent], follows the active
 * [com.fdzaki.adshield.ui.theme.AppThemeVariant]), for the one or two
 * calls-to-action per screen that should read as the "main" control.
 * [TactileButtonVariant.Secondary] = neutral panel surface — replaces
 * `OutlinedButton` at secondary-action call sites (e.g. "Reset statistik").
 */
enum class TactileButtonVariant { Primary, Secondary }

@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TactileButtonVariant = TactileButtonVariant.Secondary,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(
        targetValue = if (isPressed) TactileTokens.elevationRaisedPressed else TactileTokens.elevationRaised,
        animationSpec = tween(100),
        label = "tactileButtonElevation"
    )

    // v4.7.0: was hardcoded ShieldGreen — Primary is a decorative "main
    // action" role, not a protection-state indicator, so it now follows the
    // theme toggle instead of always being the same color as the ring.
    val trimAccent = com.fdzaki.adshield.ui.theme.LocalTrimAccent.current

    val contentColor = when (variant) {
        TactileButtonVariant.Primary -> androidx.compose.ui.graphics.Color(0xFF0B1F12)
        TactileButtonVariant.Secondary -> Ink
    }

    Box(
        modifier = modifier
            .panelShadow(elevation, shape)
            .clip(shape)
            .background(
                when (variant) {
                    TactileButtonVariant.Primary ->
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(trimAccent, trimAccent.copy(alpha = 0.82f))
                        )
                    TactileButtonVariant.Secondary ->
                        if (isPressed) TactileTokens.raisedBrushPressed() else TactileTokens.raisedBrush()
                }
            )
            .bevelBorder(shape, accent = variant == TactileButtonVariant.Primary, accentColor = trimAccent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContentColor provides if (enabled) contentColor else ShieldTextMuted,
            content = content
        )
    }
}
