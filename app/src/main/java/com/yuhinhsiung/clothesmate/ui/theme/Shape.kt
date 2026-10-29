package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Atelier 圆角：control 12 / card 16 / chip 20 / sheet 24。
 * 圆形（FAB、色点、日期标识）用 [FullCornerShape]，不是第五套圆角。
 */
val ClothesMateShapes = Shapes(
    extraSmall = RoundedCornerShape(12),
    small = RoundedCornerShape(16),
    medium = RoundedCornerShape(20),
    large = RoundedCornerShape(24),
    extraLarge = RoundedCornerShape(24),
)

val FullCornerShape = RoundedCornerShape(percent = 50)
