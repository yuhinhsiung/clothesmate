package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣柜选择行列表 —— UI 文档 §7.2「选择衣柜弹层」。
 *
 * 两个地方复用：M1-08 选择衣柜弹层、M1-07 删除去向弹层的「转移到其他衣柜」行内选择。
 * 行 = 封面缩略图（无封面用衣架图标）+ 名称 + 件数 + 右侧单选圈，按 sortOrder 排列。
 */
@Composable
fun WardrobePickerList(
    wardrobes: List<Wardrobe>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(wardrobes, key = { it.id }) { wardrobe ->
            WardrobePickerRow(
                wardrobe = wardrobe,
                selected = wardrobe.id == selectedId,
                onClick = { onSelect(wardrobe.id) },
            )
        }
    }
}

@Composable
private fun WardrobePickerRow(
    wardrobe: Wardrobe,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面缩略图 40dp 圆角；无封面用衣架图标兜底
        Box(
            modifier = Modifier
                .size(Dimens.Space.s8)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.muted),
            contentAlignment = Alignment.Center,
        ) {
            val path = wardrobe.coverImagePath
            if (path != null) {
                AsyncImage(model = path, contentDescription = null)
            } else {
                Icon(
                    imageVector = AppIcons.Wardrobe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(Dimens.Icon.m),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.Space.s3),
        ) {
            Text(
                text = wardrobe.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.wardrobe_item_count, wardrobe.clothingCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }

        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** 单选圈 + 封面 + 名称 + 件数的紧凑版（弹层内嵌使用，无分隔线需求时更轻）。 */
@Composable
fun WardrobeRadioRow(
    wardrobe: Wardrobe,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = Dimens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            text = wardrobe.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.wardrobe_item_count, wardrobe.clothingCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}

/** 行间分隔线（outline 色） */
@Composable
fun PickerDivider() {
    HorizontalDivider(
        thickness = Dimens.dividerThickness,
        color = MaterialTheme.colorScheme.outline,
    )
}
