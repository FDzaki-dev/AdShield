package com.fdzaki.adshield.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Matte premium shape scale — consistently large, confident radii instead of
 * Material's default tight 4/8/12dp ladder. Large radii + hairline borders
 * (see [ShieldOutline] in Color.kt) read as "expensive hardware" rather than
 * "stock Android component."
 */
val AdShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)
