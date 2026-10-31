package com.yuhinhsiung.clothesmate.core.platform

import kotlin.math.roundToInt

/**
 * 把 WindowMetrics 的物理宽度换算成布局 dp。
 *
 * M6-15 的 rail/底栏分档由真实平台窗口决定，不再只依赖 `BoxWithConstraints`
 * 的父容器宽度；纯函数保留在 JVM 可直测的范围里。
 */
internal fun windowWidthDp(widthPx: Int, density: Float): Int {
    if (density <= 0f) return 0
    return (widthPx / density).roundToInt()
}
