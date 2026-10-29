package com.yuhinhsiung.clothesmate.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** M6-08/M6-15：600dp 是 compact 底栏与 medium rail 的唯一断点。 */
internal enum class NavLayout { BOTTOM_BAR, RAIL }

internal fun navLayoutFor(width: Dp): NavLayout =
    if (width >= 600.dp) NavLayout.RAIL else NavLayout.BOTTOM_BAR
