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
 * 宽屏保护：手机竖屏保持全宽；平板/横屏把页面内容限制在可读宽度并居中，
 * 避免单列卡片被拉伸成巨幅留白（M5-07）。
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 840.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth > maxContentWidth) {
            Box(
                modifier = Modifier
                    .width(maxContentWidth)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
            ) { content() }
        } else {
            content()
        }
    }
}
