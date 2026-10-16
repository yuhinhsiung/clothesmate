package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.ui.component.ChipOption
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec
import com.yuhinhsiung.clothesmate.ui.component.HighlightedText
import com.yuhinhsiung.clothesmate.ui.component.SelectableChip
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 全局搜索结果页 —— 开发计划 M1-16 / 开发文档 §2.1.2 W-07、UI 文档 §7.2、mockup-19。
 *
 * 顶部搜索框保持焦点可改词 + 右侧「取消」；结果按「衣物 / 穿搭」分组；命中关键词
 * `primary` 色高亮；筛选 chips（类别 / 颜色 / 风格）；空结果空态照 §6.9 文案表（无主按钮）。
 * 分词 AND、跨衣柜由 ClothingRepository.search 保证（M1-10）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onClothingClick: (Long) -> Unit,
    onOutfitClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 保持焦点可改词（§7.2）
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s2),
            ) {
                CmTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.wardrobe_search_hint),
                    singleLine = true,
                    imeAction = ImeAction.Search,
                    focusRequester = focusRequester,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) {
                    Text(
                        text = stringResource(R.string.search_cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // 筛选 chips（类别 / 颜色 / 风格）
            if (uiState.styleOptions.isNotEmpty()) {
                FilterChipRow(
                    options = uiState.categoryOptions.map { ChipOption(it.first, it.second) },
                    selectedKey = uiState.categoryFilter,
                    onSelect = viewModel::onCategoryFilter,
                )
            }
            FilterChipRow(
                options = uiState.colorOptions.map { color ->
                    ChipOption(
                        key = color.key,
                        label = color.displayName,
                        color = com.yuhinhsiung.clothesmate.ui.theme.ClothingColors.dotColor(color),
                    )
                },
                selectedKey = uiState.colorFilter,
                onSelect = viewModel::onColorFilter,
            )
            if (uiState.styleOptions.isNotEmpty()) {
                FilterChipRow(
                    options = uiState.styleOptions.map { ChipOption(it, it) },
                    selectedKey = uiState.styleFilter,
                    onSelect = viewModel::onStyleFilter,
                )
            }

            when {
                // 空结果（有词或筛过）：§6.9「搜索无结果」，无主按钮
                uiState.items.isEmpty() && uiState.outfits.isEmpty() &&
                    (uiState.query.isNotBlank() || uiState.hasAnyFilter) -> {
                    com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                        spec = EmptyStateSpec(
                            icon = AppIcons.Search,
                            title = stringResource(R.string.empty_search_title, uiState.query),
                            description = stringResource(R.string.empty_search_desc),
                            actionLabel = null,
                            onAction = null,
                        ),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Dimens.pagePadding,
                            vertical = Dimens.Space.s2,
                        ),
                    ) {
                        if (uiState.items.isNotEmpty()) {
                            item(key = "header_clothing") {
                                SectionHeader(
                                    stringResource(
                                        R.string.search_section_clothing,
                                        uiState.items.size,
                                    ),
                                )
                            }
                            items(uiState.items, key = { "item_${it.id}" }) { item ->
                                ClothingResultRow(
                                    item = item,
                                    wardrobeName = uiState.wardrobeNames[item.wardrobeId],
                                    tokens = uiState.tokens,
                                    onClick = { onClothingClick(item.id) },
                                )
                            }
                        }
                        if (uiState.outfits.isNotEmpty()) {
                            item(key = "header_outfit") {
                                SectionHeader(
                                    stringResource(
                                        R.string.search_section_outfit,
                                        uiState.outfits.size,
                                    ),
                                )
                            }
                            // 穿搭双列卡片（§7.2）：成对行排布
                            uiState.outfits.chunked(2).forEachIndexed { rowIndex, pair ->
                                item(key = "outfit_row_$rowIndex") {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = Dimens.Space.s2),
                                    ) {
                                        pair.forEach { outfit ->
                                            OutfitResultCard(
                                                outfit = outfit,
                                                tokens = uiState.tokens,
                                                onClick = { onOutfitClick(outfit.id) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (pair.size == 1) {
                                            androidx.compose.foundation.layout.Spacer(
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    options: List<ChipOption>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Dimens.pagePadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        items(options, key = { it.key }) { option ->
            SelectableChip(
                option = option,
                selected = selectedKey == option.key,
                onClick = { onSelect(if (selectedKey == option.key) null else option.key) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = Dimens.Space.s2),
    )
}

/** 衣物结果行：缩略图 + 编号 + 名称（高亮）+ 所属衣柜（§7.2）。 */
@Composable
private fun ClothingResultRow(
    item: ClothingItem,
    wardrobeName: String?,
    tokens: List<String>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.Space.s8)
                .clip(RoundedCornerShape(Dimens.Radius.s))
                .background(MaterialTheme.colorScheme.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (item.displayImagePath != null) {
                CmAsyncImage(
                    model = item.displayImagePath,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                )
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
            HighlightedText(
                text = item.displayName,
                tokens = tokens,
                style = MaterialTheme.typography.titleMedium,
            )
            HighlightedText(
                text = "No.${item.code}" + (wardrobeName?.let { " · $it" } ?: ""),
                tokens = tokens,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 穿搭结果卡：封面 + 名称（高亮），双列（§7.2）。 */
@Composable
private fun OutfitResultCard(
    outfit: Outfit,
    tokens: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(Dimens.Radius.l))
                .background(MaterialTheme.colorScheme.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (outfit.coverPath != null) {
                CmAsyncImage(
                    model = outfit.coverPath,
                    contentDescription = outfit.displayName,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = AppIcons.Outfit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(Dimens.Icon.l),
                )
            }
        }
        HighlightedText(
            text = outfit.displayName,
            tokens = tokens,
            modifier = Modifier.padding(top = Dimens.Space.s1),
        )
    }
}
