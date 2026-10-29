package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import com.yuhinhsiung.clothesmate.ui.component.rememberPhotoAspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.RootHeader
import com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec
import com.yuhinhsiung.clothesmate.ui.component.SelectableChip
import com.yuhinhsiung.clothesmate.ui.component.ChipOption
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.OnPhoto
import com.yuhinhsiung.clothesmate.ui.theme.PhotoScrimLight
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 穿搭瀑布流 —— 开发计划 M1-19 / 开发文档 §2.3.2 O-03/O-04、UI 文档 §6.5/§7.4、mockup-08。
 *
 * 双列 `LazyVerticalStaggeredGrid`、列距 8dp；卡片 = 照片占满 + 左下名称（白字 + 底部
 * 20% 渐变蒙层）+ 右上件数 chip。顶部标签筛选条（风格/场景/季节 chips 横滑）。
 *
 * 高度错落：照片加载前按 id 确定性地给三种纵横比之一（4:5 / 3:4 / 1:1），既保证
 * 瀑布流错落自然，又避免加载后高度跳变；照片实际比例由 ContentScale.Crop 填充。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitWaterfallRoute(
    onBack: () -> Unit,
    onOutfitClick: (Long) -> Unit,
    onCreateOutfit: () -> Unit,
    viewModel: OutfitWaterfallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()
    val styleSelection by viewModel.styleSelection().collectAsStateWithLifecycle()
    val sceneSelection by viewModel.sceneSelection().collectAsStateWithLifecycle()
    val seasonSelection by viewModel.seasonSelection().collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            RootHeader(
                title = stringResource(R.string.outfit_home_title),
                slogan = stringResource(R.string.outfit_slogan),
            ) {
                if (categoryName != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.availableTags.isEmpty) {
                OutfitFilterBar(
                    tags = state.availableTags,
                    styleSelection = styleSelection,
                    sceneSelection = sceneSelection,
                    seasonSelection = seasonSelection,
                    onToggleStyle = viewModel::toggleStyle,
                    onToggleScene = viewModel::toggleScene,
                    onToggleSeason = viewModel::toggleSeason,
                )
            }

            when {
                // 首帧数据未到（review R-12：与「真的没有穿搭」分开，避免闪一下空态）
                state.loading -> {
                    Column(
                        modifier = Modifier.padding(Dimens.pagePadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                    ) {
                        repeat(3) {
                            com.yuhinhsiung.clothesmate.ui.component.ShimmerCard(height = 120.dp)
                        }
                    }
                }

                // 源列表为空才是「这里还没有穿搭」（review B-11 + R-12）：
                // 分类内写「这个分类还没有穿搭」，全部入口写「把喜欢的搭配存下来」
                state.sourceCount == 0 -> {
                    val spec = if (viewModel.isCategoryScoped) {
                        EmptyStateSpec(
                            icon = AppIcons.Tag,
                            title = stringResource(R.string.empty_category_no_outfit_title),
                            description = stringResource(R.string.empty_category_no_outfit_desc),
                            actionLabel = stringResource(R.string.outfit_create),
                            onAction = onCreateOutfit,
                        )
                    } else {
                        EmptyStateSpec(
                            icon = AppIcons.Outfit,
                            title = stringResource(R.string.empty_no_outfit_title),
                            description = stringResource(R.string.empty_no_outfit_desc),
                            actionLabel = stringResource(R.string.outfit_create),
                            onAction = onCreateOutfit,
                        )
                    }
                    com.yuhinhsiung.clothesmate.ui.component.EmptyState(spec = spec)
                }

                // 源列表有内容、过滤后为空 = **筛选没命中**（review R-12 的核心分支）。
                // 原先这里会被上一分支吞掉，还甩出一个「创建穿搭」按钮。
                state.outfits.isEmpty() && state.hasActiveFilter -> {
                    com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                        spec = EmptyStateSpec(
                            icon = AppIcons.Filter,
                            title = stringResource(R.string.empty_outfit_filter_none_title),
                            description = stringResource(R.string.empty_outfit_filter_none_desc),
                            actionLabel = stringResource(R.string.outfit_filter_clear),
                            onAction = viewModel::clearFilters,
                        ),
                    )
                }

                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(Dimens.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.waterfallColumnSpacing),
                        verticalItemSpacing = Dimens.waterfallColumnSpacing,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.outfits, key = { it.id }) { outfit ->
                            Box(Modifier.animateItem()) {
                                OutfitWaterfallCard(
                                    outfit = outfit,
                                    onClick = { onOutfitClick(outfit.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 顶部标签筛选条：风格 / 场景 / 季节三组 chips 横滑。 */
@Composable
private fun OutfitFilterBar(
    tags: AvailableTags,
    styleSelection: Set<String>,
    sceneSelection: Set<String>,
    seasonSelection: Set<String>,
    onToggleStyle: (String) -> Unit,
    onToggleScene: (String) -> Unit,
    onToggleSeason: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        modifier = Modifier.padding(vertical = Dimens.Space.s2),
    ) {
        tags.styles.forEach { tag ->
            item(key = "style_$tag") {
                SelectableChip(
                    option = ChipOption(key = tag, label = tag),
                    selected = tag in styleSelection,
                    onClick = { onToggleStyle(tag) },
                )
            }
        }
        tags.scenes.forEach { tag ->
            item(key = "scene_$tag") {
                SelectableChip(
                    option = ChipOption(key = tag, label = tag),
                    selected = tag in sceneSelection,
                    onClick = { onToggleScene(tag) },
                )
            }
        }
        tags.seasons.forEach { tag ->
            item(key = "season_$tag") {
                SelectableChip(
                    option = ChipOption(
                        key = tag,
                        label = com.yuhinhsiung.clothesmate.core.model.Season.fromKey(tag)?.displayName ?: tag,
                    ),
                    selected = tag in seasonSelection,
                    onClick = { onToggleSeason(tag) },
                )
            }
        }
    }
}

@Composable
private fun OutfitWaterfallCard(
    outfit: Outfit,
    onClick: () -> Unit,
) {
    val cover = outfit.coverPath
    // M6：已知 EXIF 校正比例；未知本次用 3:4 槽。不再用 id 伪造错落。
    val ratio = rememberPhotoAspectRatio(cover) ?: 0.75f
    val label = outfit.sceneTags.firstOrNull()?.let { tag ->
        stringResource(R.string.outfit_ident_scene, tag, outfit.itemCount)
    } ?: stringResource(R.string.outfit_items_only, outfit.itemCount)
    val createdAt = remember(outfit.createdAt) {
        java.text.SimpleDateFormat("yyyy年M月", java.util.Locale.getDefault())
            .format(java.util.Date(outfit.createdAt))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius.m))
            .clickable(onClick = onClick)
            .padding(bottom = Dimens.Space.s1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(Dimens.Radius.m))
                .background(MaterialTheme.colorScheme.muted),
        ) {
            if (cover != null) {
                CmAsyncImage(
                    model = cover,
                    contentDescription = label,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = AppIcons.Outfit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(Dimens.Icon.l + Dimens.Space.s4),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.Space.s2)
                    .clip(RoundedCornerShape(50))
                    .background(PhotoScrimLight)
                    .padding(horizontal = Dimens.Space.s2, vertical = Dimens.Space.s1),
            ) {
                Text(
                    text = stringResource(R.string.outfit_card_count, outfit.itemCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnPhoto,
                )
            }
        }
        Spacer(Modifier.height(Dimens.Space.s2))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.outfit_created_at, createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}

/** 底部渐变蒙层的形状因子：与卡片同宽、高为宽的 1/3（约等于「底部 20%+」视觉） */
private const val SCRIM_ASPECT = 3f
