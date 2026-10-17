package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.ui.theme.Dimens

/**
 * 标签 / 筛选 chips 组 —— UI 文档 §6.6。
 *
 * - 选择 chip：未选中 `surface` + `outline` 描边；选中 `primaryContainer` 底 + `onPrimaryContainer` 字；
 * - 表单里的标签组用 [FlowRow] 换行铺开（360dp 小屏不溢出）；
 * - 可选色点（颜色 12 色点选用，§6.5）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(
    options: List<ChipOption>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** M2-04：AI 预填的选中 chip 描边变金色（UI 文档 §6.6）；用户改动后传入空集 */
    accentSelectedKeys: Set<String> = emptySet(),
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        options.forEach { option ->
            SelectableChip(
                option = option,
                selected = option.key in selectedKeys,
                accentBorder = option.key in accentSelectedKeys,
                onClick = { onToggle(option.key) },
            )
        }
    }
}

@Composable
fun SelectableChip(
    option: ChipOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** AI 预填的选中态：金色 2dp 描边（§6.6） */
    accentBorder: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = option.color?.let { color ->
            { ColorDot(color = color) }
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = if (selected && accentBorder) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        },
        modifier = modifier,
    )
}

/** 色点（§6.5 颜色色块）。白色点加描边否则浅底上看不见。 */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimens.Icon.s)
            .clip(CircleShape)
            .background(color)
            .then(
                if (color == Color.White) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

/** chip 选项。`key` 与调用方的数据键对齐（枚举 key / 自定义标签原文）。 */
data class ChipOption(
    val key: String,
    val label: String,
    /** 可选：色点（颜色 12 色点选用，UI 文档 §6.5） */
    val color: Color? = null,
)
