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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
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
    val outfits by viewModel.outfits.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()
    val styleSelection by viewModel.styleSelection().collectAsStateWithLifecycle()
    val sceneSelection by viewModel.sceneSelection().collectAsStateWithLifecycle()
    val seasonSelection by viewModel.seasonSelection().collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = categoryName
                            ?: stringResource(R.string.outfit_all_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(Dimens.Space.s3),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!availableTags.isEmpty) {
                OutfitFilterBar(
                    tags = availableTags,
                    styleSelection = styleSelection,
                    sceneSelection = sceneSelection,
                    seasonSelection = seasonSelection,
                    onToggleStyle = viewModel::toggleStyle,
                    onToggleScene = viewModel::toggleScene,
                    onToggleSeason = viewModel::toggleSeason,
                )
            }

            when {
                // review B-11：分类内无穿搭必须是「这个分类还没有穿搭」空态——
                // 此前以「数据源空 + 筛选条空」判定，而筛选条恒取全部穿搭 → 空分类
                // 被误判成「筛选无结果」+ 一排无关 chips
                viewModel.isCategoryScoped && outfits.isEmpty() -> {
                    com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                        spec = EmptyStateSpec(
                            icon = AppIcons.Tag,
                            title = stringResource(R.string.empty_category_no_outfit_title),
                            description = stringResource(R.string.empty_category_no_outfit_desc),
                            actionLabel = stringResource(R.string.outfit_create),
                            onAction = onCreateOutfit,
                        ),
                    )
                }

                outfits.isEmpty() && availableTags.isEmpty -> {
                    // 无穿搭（O-04）：全部穿搭入口在数据源为空时（无任何标签可选）
                    com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                        spec = EmptyStateSpec(
                            icon = AppIcons.Outfit,
                            title = stringResource(R.string.empty_no_outfit_title),
                            description = stringResource(R.string.empty_no_outfit_desc),
                            actionLabel = stringResource(R.string.outfit_create),
                            onAction = onCreateOutfit,
                        ),
                    )
                }

                outfits.isEmpty() -> com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                    spec = EmptyStateSpec(
                        icon = AppIcons.Filter,
                        title = stringResource(R.string.empty_outfit_filter_none_title),
                        description = null,
                        actionLabel = null,
                        onAction = null,
                    ),
                )

                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(Dimens.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.waterfallColumnSpacing),
                        verticalItemSpacing = Dimens.waterfallColumnSpacing,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(outfits, key = { it.id }) { outfit ->
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardAspectRatio(outfit.id))
            .clip(RoundedCornerShape(Dimens.Radius.l))
            .background(MaterialTheme.colorScheme.muted)
            .clickable(onClick = onClick),
    ) {
        val cover = outfit.coverPath
        if (cover != null) {
            CmAsyncImage(
                model = cover,
                contentDescription = outfit.displayName,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 底部 20% 黑色渐变蒙层（§6.5）：名称可读性
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .aspectRatio(SCRIM_ASPECT)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, PhotoScrimLight),
                        ),
                    ),
            )
        } else {
            // 无照片穿搭：muted 占位，不给破图（M1-19 验收）
            Icon(
                imageVector = AppIcons.Outfit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(Dimens.Icon.l + Dimens.Space.s4),
            )
        }

        // 右上角件数 chip（黑 40% 底白字，§6.5）
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

        // 左下名称（白字 + 蒙层，§6.5）
        Text(
            text = outfit.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = OnPhoto,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Dimens.Space.s3),
        )
    }
}

/** 卡片纵横比按 id 确定性错落（4:5 / 3:4 / 1:1 循环），避免加载后高度跳变 */
private fun cardAspectRatio(id: Long): Float = when ((id % 3).toInt()) {
    0 -> 4f / 5f
    1 -> 3f / 4f
    else -> 1f
}

/** 底部渐变蒙层的形状因子：与卡片同宽、高为宽的 1/3（约等于「底部 20%+」视觉） */
private const val SCRIM_ASPECT = 3f
