package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.component.ShimmerCard
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣柜列表页 —— 开发计划 M1-05 / 开发文档 §2.1.2 W-03/W-08、UI 文档 §6.5/§7.2。
 */
@Composable
fun WardrobeRoute(
    onWardrobeClick: (Long) -> Unit = {},
    onShowAddSheet: () -> Unit = {},
    onShowDelete: (Long) -> Unit = {},
    viewModel: WardrobeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WardrobeListScreen(
        uiState = uiState,
        onWardrobeClick = onWardrobeClick,
        onAddWardrobe = onShowAddSheet,
        onWardrobeLongPress = onShowDelete,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeListScreen(
    uiState: WardrobeListUiState,
    onWardrobeClick: (Long) -> Unit = {},
    onAddWardrobe: () -> Unit = {},
    onWardrobeLongPress: (Long) -> Unit = {},
    viewModel: WardrobeListViewModel = hiltViewModel(),
) {
    // 拖拽重排时的本地顺序：松手后才经 ViewModel 落库，拖拽中只动 UI
    val reordered = remember { mutableStateListOf<Wardrobe>() }
    val wardrobes = when (val s = uiState) {
        is WardrobeListUiState.Content -> s.wardrobes
        else -> emptyList()
    }
    if (reordered.size != wardrobes.size) {
        reordered.clear()
        reordered.addAll(wardrobes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wardrobe_title), style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddWardrobe,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(AppIcons.Add, contentDescription = stringResource(R.string.wardrobe_add))
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                is WardrobeListUiState.Loading -> {
                    // 列表加载用骨架屏，不用转圈（§6.9）
                    Column(
                        modifier = Modifier.padding(Dimens.pagePadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                    ) {
                        repeat(4) { ShimmerCard(height = 120.dp) }
                    }
                }

                is WardrobeListUiState.Empty -> {
                    EmptyWardrobeState(onAddWardrobe = onAddWardrobe)
                }

                is WardrobeListUiState.Content -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.waterfallColumnSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimens.waterfallColumnSpacing),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = reordered,
                            key = { _, w -> w.id },
                        ) { _, wardrobe ->
                            WardrobeCard(
                                wardrobe = wardrobe,
                                onClick = { onWardrobeClick(wardrobe.id) },
                                onMore = { onWardrobeLongPress(wardrobe.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeCard(
    wardrobe: Wardrobe,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column {
                // 封面 16:9；无封面时用 muted 底 + 衣架图标占位（§6.5）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageFile = wardrobe.coverImagePath
                    if (imageFile != null) {
                        AsyncImage(
                            model = imageFile,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AppIcons.Wardrobe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onMuted,
                                modifier = Modifier.size(Dimens.Icon.l),
                            )
                        }
                    }
                }

                // 名称 Title M + 件数 Body M（onMuted）
                Column(modifier = Modifier.padding(Dimens.Space.s3)) {
                    Text(
                        text = wardrobe.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.wardrobe_item_count, wardrobe.clothingCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(top = Dimens.Space.s1),
                    )
                }
            }

            // 右上角「更多」图标（§6.5 衣柜卡规格）→ 编辑 / 删除
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = AppIcons.More,
                    contentDescription = stringResource(R.string.wardrobe_more),
                    tint = MaterialTheme.colorScheme.onMuted,
                )
            }
        }
    }
}

@Composable
private fun EmptyWardrobeState(onAddWardrobe: () -> Unit) {
    // 空态四要素照 UI 文档 §6.9 表格「无任何衣柜」行
    com.yuhinhsiung.clothesmate.ui.component.EmptyState(
        spec = com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec(
            icon = AppIcons.Wardrobe,
            title = stringResource(R.string.empty_no_wardrobe_title),
            description = stringResource(R.string.empty_no_wardrobe_desc),
            actionLabel = stringResource(R.string.wardrobe_add),
            onAction = onAddWardrobe,
        ),
    )
}
