package com.fdzaki.adshield.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * v4.4.0 — "Radikal Redesign" (see Color.kt kdoc + PROJECT_STATE.md). Radii
 * pulled in from the previous "glass card" scale (10-34dp) to a machined-panel
 * scale (8-22dp): a hardware instrument panel reads as milled/cut, not as a
 * soft floating pane — overly large radii undercut the skeuomorphic-lite
 * direction by looking like the flat-glass identity this replaces.
 */
val AdShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)
