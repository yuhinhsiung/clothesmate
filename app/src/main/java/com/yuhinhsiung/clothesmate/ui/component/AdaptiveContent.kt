package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 宽屏保护：手机竖屏保持全宽；medium（600–840dp）限制到 600dp，expanded（≥840dp）
 * 限制到 [maxContentWidth] 并居中，避免单列卡片被拉伸成巨幅留白（M5-07）。
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 840.dp,
    mediumContentWidth: Dp = 600.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentWidth = adaptiveContentWidth(maxWidth, maxContentWidth, mediumContentWidth)
        if (contentWidth < maxWidth) {
            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
            ) { content() }
        } else {
            content()
        }
    }
}

/** 按断点选择实际内容宽度；抽成纯函数便于 JVM 单测（M5-07）。 */
internal fun adaptiveContentWidth(
    maxWidth: Dp,
    maxContentWidth: Dp = 840.dp,
    mediumContentWidth: Dp = 600.dp,
): Dp {
    val cap = if (maxWidth >= 840.dp) maxContentWidth else mediumContentWidth
    return if (maxWidth > cap) cap else maxWidth
}
