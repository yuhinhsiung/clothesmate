package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣物选择器 —— 开发计划 M1-18 / UI 文档 §7.4、mockup-16。
 *
 * 搜索框 + 两行筛选 chips（衣柜行 / 类别行）+「已收起 n」显隐切换 + 3 列多选网格
 * （选中 = primary 2dp 描边 + 右上玫瑰圆底白序号，序号 = 选择顺序）+ 底部「已选 n 件 / 完成」。
 * 系统返回 = 放弃本次改动（不回传结果）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingPickerRoute(
    onDone: (List<Long>) -> Unit,
    viewModel: ClothingPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.picker_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            // 底部固定操作条：左「已选 n 件」右「完成」（§7.4）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.picker_selected_count, uiState.selectedIds.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = { onDone(uiState.selectedIds) },
                    enabled = uiState.selectedIds.isNotEmpty(),
                    shape = RoundedCornerShape(Dimens.Radius.m),
                    modifier = Modifier.height(Dimens.buttonHeight),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary
                            .copy(alpha = Dimens.DisabledAlpha),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            .copy(alpha = Dimens.DisabledAlpha),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.picker_done),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 搜索框（按编号 / 颜色 / 类别过滤，§7.4）
            CmTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = stringResource(R.string.picker_search_label),
                placeholder = "编号 / 颜色 / 类别",
                imeAction = ImeAction.Search,
                modifier = Modifier
                    .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s2),
            )

            // 衣柜行 chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            ) {
                item(key = "wardrobe_all") {
                    PickerChip(
                        label = stringResource(R.string.filter_wardrobe_all),
                        selected = uiState.wardrobeFilter == null,
                        onClick = { viewModel.onWardrobeFilter(null) },
                    )
                }
                items(uiState.wardrobes, key = { it.id }) { wardrobe ->
                    PickerChip(
                        label = wardrobe.name,
                        selected = uiState.wardrobeFilter == wardrobe.id,
                        onClick = { viewModel.onWardrobeFilter(wardrobe.id) },
                    )
                }
            }

            // 类别行 chips（大类合并词「鞋包配饰」同 UI 文档 §7.4）+「已收起 n」
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                modifier = Modifier.padding(top = Dimens.Space.s2),
            ) {
                item(key = "cat_all") {
                    PickerChip(
                        label = stringResource(R.string.filter_category_all),
                        selected = uiState.categoryFilter == null,
                        onClick = { viewModel.onCategoryFilter(null) },
                    )
                }
                items(PickerCategories, key = { it.first }) { (key, label) ->
                    PickerChip(
                        label = label,
                        selected = uiState.categoryFilter == key,
                        onClick = { viewModel.onCategoryFilter(key) },
                    )
                }
                item(key = "stored_toggle") {
                    if (uiState.storedCount > 0) {
                        PickerChip(
                            label = stringResource(R.string.picker_stored_toggle, uiState.storedCount),
                            selected = uiState.includeStored,
                            onClick = viewModel::toggleIncludeStored,
                        )
                    }
                }
            }

            if (uiState.items.isEmpty()) {
                com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                    spec = EmptyStateSpec(
                        icon = AppIcons.Wardrobe,
                        title = stringResource(R.string.empty_picker_no_items_title),
                        description = stringResource(R.string.empty_picker_no_items_desc),
                        actionLabel = null,
                        onAction = null,
                    ),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(Dimens.ClothingGridColumns),
                    contentPadding = PaddingValues(Dimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        PickerGridCell(
                            item = item,
                            order = uiState.selectedIds.indexOf(item.id).takeIf { it >= 0 }?.plus(1),
                            onToggle = { viewModel.toggleSelection(item.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 类别行筛选词（UI 文档 §7.4：全部 / 上衣 / 外套 / 下装 / 连衣裙 / 鞋包配饰）。
 * 「鞋包配饰」合并鞋子 / 包包 / 帽子 / 配饰四个大类——帽子按语义并入配饰侧
 * （大类词命中逻辑与 ClothingCategory.searchAliases 的合并词一致）。
 */
private val PickerCategories: List<Pair<String, String>> = listOf(
    "top" to "上衣",
    "outer" to "外套",
    "bottom" to "下装",
    "dress" to "连衣裙",
    ClothingCategory.ShoesOther.topKey to "鞋包配饰",
)

@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    )
}

/** 多选网格单元：选中 = primary 2dp 描边 + 右上序号圆点（§7.4）。 */
@Composable
private fun PickerGridCell(
    item: ClothingItem,
    order: Int?,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Dimens.Radius.m))
            .background(MaterialTheme.colorScheme.muted)
            .then(
                if (order != null) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.Radius.m))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onToggle),
    ) {
        val image = item.displayImagePath
        if (image != null) {
            CmAsyncImage(
                model = image,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = AppIcons.Wardrobe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(Dimens.Icon.l),
            )
        }
        if (item.isStored) {
            // 选择器里被显式展开的收起衣物带「已收起」底条（W-09 标记可见）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(vertical = Dimens.Space.s1),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.clothing_stored_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
        }
        if (order != null) {
            // 玫瑰圆底白序号（①②③ = 选择顺序，与确认页横滑列一致）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.Space.s1)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = order.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
