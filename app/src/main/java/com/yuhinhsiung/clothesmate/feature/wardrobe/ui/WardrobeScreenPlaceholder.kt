package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 衣柜 Tab 占位 —— M1-05 实装（衣柜卡网格 + 搜索框 + FAB + 拖拽排序）。
 * P0 阶段仅保证导航与主题跑通。
 */
@Composable
fun WardrobeRoute() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "衣柜",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
