package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 穿搭 Tab 占位 —— M1-17 实装（分类首页 + 全部穿搭入口）。
 * P0 阶段仅保证导航与主题跑通。
 */
@Composable
fun OutfitHomeRoute() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "穿搭",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
