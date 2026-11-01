package com.yuhinhsiung.clothesmate.core.platform

/**
 * M6-15：分隔折痕的避让结果，单位 dp。
 *
 * 应用没有双栏主从结构，因此折叠/半开时把单任务放到一侧 pane，避免内容跨过
 * 物理铰链；另一侧留空。两侧都小于 [MIN_PANE_DP] 时不做避让，退回整窗单任务。
 */
internal data class FoldPadding(
    val startDp: Int = 0,
    val endDp: Int = 0,
    val topDp: Int = 0,
    val bottomDp: Int = 0,
    val effectiveWidthDp: Int? = null,
)

internal const val MIN_PANE_DP = 320

internal fun foldAvoidance(
    separating: Boolean,
    vertical: Boolean,
    hingeStartPx: Int,
    hingeEndPx: Int,
    windowWidthPx: Int,
    windowHeightPx: Int,
    density: Float,
): FoldPadding {
    if (!separating || density <= 0f) return FoldPadding()
    if (vertical) {
        val left = (hingeStartPx / density).toInt()
        val right = ((windowWidthPx - hingeEndPx) / density).toInt()
        return when {
            left >= MIN_PANE_DP -> FoldPadding(
                endDp = ((windowWidthPx - hingeStartPx) / density).toInt().coerceAtLeast(0),
                effectiveWidthDp = left,
            )
            right >= MIN_PANE_DP -> FoldPadding(
                startDp = (hingeEndPx / density).toInt().coerceAtLeast(0),
                effectiveWidthDp = right,
            )
            else -> FoldPadding()
        }
    }
    val top = (hingeStartPx / density).toInt()
    val bottom = ((windowHeightPx - hingeEndPx) / density).toInt()
    return when {
        top >= MIN_PANE_DP -> FoldPadding(
            bottomDp = ((windowHeightPx - hingeStartPx) / density).toInt().coerceAtLeast(0),
        )
        bottom >= MIN_PANE_DP -> FoldPadding(
            topDp = (hingeEndPx / density).toInt().coerceAtLeast(0),
        )
        else -> FoldPadding()
    }
}
