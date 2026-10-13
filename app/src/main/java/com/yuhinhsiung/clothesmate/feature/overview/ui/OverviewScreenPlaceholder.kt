package com.yuhinhsiung.clothesmate.feature.overview.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 概览 Tab 占位 —— M1-22 实装（数字卡 + 类别环形图 + 颜色分布条）。
 * P0 阶段仅保证导航与主题跑通。
 */
@Composable
fun OverviewRoute() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "概览",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
