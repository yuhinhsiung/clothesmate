package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.LocalAtelierColors

/**
 * M6 根页标题：大页面名在上（28/600），小 slogan 在下（14/400 次文字色）。
 *
 * 不使用 TopAppBar 的居中/单行标题语义，避免把 slogan 提升为主标题。
 */
@Composable
fun RootHeader(
    title: String,
    slogan: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalAtelierColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Dimens.pagePadding, end = Dimens.pagePadding, top = Dimens.Space.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = slogan,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            actions()
        }
    }
}
