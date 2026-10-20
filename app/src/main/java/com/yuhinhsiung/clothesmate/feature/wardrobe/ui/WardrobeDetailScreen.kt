package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeItemFilter
import com.yuhinhsiung.clothesmate.ui.component.ChipGroup
import com.yuhinhsiung.clothesmate.ui.component.ChipOption
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.navigation.sharedClothingImage
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.accentText
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣柜详情页 —— 开发计划 M1-09 / 开发文档 §2.1.2 W-04/W-09、UI 文档 §7.2、mockup-04。
 *
 * 顶部信息条（件数 + 备注）+ 返回 + 更多菜单（review C-12：编辑/删除衣柜入口）；
 * 衣物 3 列网格；状态筛选 chips（全部/正常/已收起/待完善）；空态「这个衣柜还是空的」+ [+ 录衣服]。
 *
 * 衣柜 id 不经形参传入：ViewModel 从 SavedStateHandle 自取（与 ClothingDetail 同模式，
 * review E-c——原先的形参是死参）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeDetailRoute(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit = {},
    /** M4-02：待完善衣物直接进编辑确认页补全 */
    onIncompleteClick: (Long) -> Unit = onItemClick,
    /** 录衣服：带当前衣柜 id 作为录入确认页预选（M1-14） */
    onAddClothing: (Long?) -> Unit = {},
    onEditWardrobe: () -> Unit = {},
    onDeleteWardrobe: () -> Unit = {},
    viewModel: WardrobeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingSeasonStore by remember { mutableStateOf<Boolean?>(null) }
    var pendingSeasonSelected by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val storedSnack = stringResource(R.string.wardrobe_season_stored_snackbar)
    val restoredSnack = stringResource(R.string.wardrobe_season_restored_snackbar)
    val undoLabel = stringResource(R.string.wardrobe_season_undo)
    val undoneText = stringResource(R.string.wardrobe_season_undone)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WardrobeDetailEvent.SeasonChanged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = if (event.stored) {
                            storedSnack.format(event.count)
                        } else {
                            restoredSnack.format(event.count)
                        },
                        actionLabel = undoLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoSeasonChange(event.operationId)
                    }
                }
                is WardrobeDetailEvent.SeasonUndone -> {
                    snackbarHostState.showSnackbar(undoneText)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.wardrobeName, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(Dimens.Space.s3),
                    )
                },
                // review C-12：M1-09 任务原文「返回 + 更多菜单」。编辑 / 删除衣柜
                // 与列表卡「更多」同款两项
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = AppIcons.More,
                                contentDescription = stringResource(R.string.wardrobe_more),
                                tint = MaterialTheme.colorScheme.onMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wardrobe_season_mode)) },
                                leadingIcon = { Icon(AppIcons.Store, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openSeasonMode()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wardrobe_menu_edit)) },
                                leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEditWardrobe()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.wardrobe_menu_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteWardrobe()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddClothing(uiState.wardrobeId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(AppIcons.Add, contentDescription = stringResource(R.string.clothing_add))
            }
        },
    ) { innerPadding ->
        // review B-22：首帧数据未到显示骨架，误显空态是 M1-23 声称覆盖的三态缺口
        if (uiState.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Dimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
            ) {
                repeat(4) {
                    com.yuhinhsiung.clothesmate.ui.component.ShimmerCard()
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 信息条：件数 + 备注
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.muted)
                    .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_item_count, uiState.items.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                uiState.wardrobeNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = " · $note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Dimens.Space.s2),
                    )
                }
            }

            // 状态筛选 chips。review U-e：改 LazyRow——360dp 小屏上四位计数
            // （如「全部 128」）在固定 Row 里会挤压溢出，横滑是最稳的兜底
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s3),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            ) {
                items(WardrobeItemFilter.entries.toList(), key = { it.name }) { filter ->
                    FilterChip(
                        selected = uiState.filter == filter,
                        onClick = { viewModel.onFilterChange(filter) },
                        label = {
                            Text(
                                text = stringResource(filter.labelRes(), filter.count(uiState.counts)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        shape = RoundedCornerShape(50),
                    )
                }
            }

            // 空态分两种：衣柜真空（§6.9 表格「衣柜内无衣物」）vs 筛选后无结果。
            // review U-d：原先筛选零结果也复用衣柜级空态，用户会困惑「衣服去哪了」。
            val filterEmpty = uiState.items.isEmpty() &&
                uiState.counts.total > 0 &&
                uiState.filter != WardrobeItemFilter.All
            when {
                filterEmpty -> com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                    spec = EmptyStateSpec(
                        icon = AppIcons.Filter,
                        title = stringResource(R.string.empty_wardrobe_filter_none_title),
                        description = null,
                        actionLabel = stringResource(R.string.filter_all, uiState.counts.total),
                        onAction = { viewModel.onFilterChange(WardrobeItemFilter.All) },
                    ),
                )

                uiState.items.isEmpty() -> com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                    spec = EmptyStateSpec(
                        // U-c：§6.9 表定「衣柜内无衣物」用衣架（checkroom）字形，
                        // 原先用了相机（photo_camera），已改
                        icon = AppIcons.Wardrobe,
                        title = stringResource(R.string.empty_wardrobe_no_items_title),
                        description = stringResource(R.string.empty_wardrobe_no_items_desc),
                        actionLabel = stringResource(R.string.clothing_add),
                        onAction = { onAddClothing(uiState.wardrobeId) },
                    ),
                )

                else -> Unit
            }

            if (uiState.items.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(Dimens.ClothingGridColumns),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        Box(Modifier.animateItem()) {
                            ClothingGridCard(
                                item = item,
                                onClick = {
                                    if (item.isIncomplete) onIncompleteClick(item.id)
                                    else onItemClick(item.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.seasonModeOpen) {
        SeasonModeSheet(
            selectedSeasons = uiState.selectedSeasons,
            items = uiState.seasonItems,
            selectedIds = uiState.seasonSelectedIds,
            normalCount = uiState.seasonNormalCount,
            storedCount = uiState.seasonStoredCount,
            selectedNormalCount = uiState.seasonSelectedNormalCount,
            selectedStoredCount = uiState.seasonSelectedStoredCount,
            onToggle = viewModel::toggleSeason,
            onToggleItem = viewModel::toggleSeasonItem,
            onSelectAll = viewModel::setAllSeasonItemsSelected,
            onDismiss = viewModel::dismissSeasonMode,
            onRequestStore = { pendingSeasonSelected = false; pendingSeasonStore = true },
            onRequestRestore = { pendingSeasonSelected = false; pendingSeasonStore = false },
            onRequestSelectedStore = { pendingSeasonSelected = true; pendingSeasonStore = true },
            onRequestSelectedRestore = { pendingSeasonSelected = true; pendingSeasonStore = false },
        )
    }

    pendingSeasonStore?.let { store ->
        val count = if (pendingSeasonSelected) {
            if (store) uiState.seasonSelectedNormalCount else uiState.seasonSelectedStoredCount
        } else {
            if (store) uiState.seasonNormalCount else uiState.seasonStoredCount
        }
        AlertDialog(
            onDismissRequest = { pendingSeasonStore = null },
            title = {
                Text(
                    stringResource(
                        if (store) {
                            R.string.wardrobe_season_confirm_store_title
                        } else {
                            R.string.wardrobe_season_confirm_restore_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (store) {
                            R.string.wardrobe_season_confirm_store_text
                        } else {
                            R.string.wardrobe_season_confirm_restore_text
                        },
                        count,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSeasonStore = null
                    if (pendingSeasonSelected) {
                        viewModel.applySelectedSeasonStored(store)
                    } else {
                        viewModel.applySeasonStored(store)
                        viewModel.dismissSeasonMode()
                    }
                }) { Text(stringResource(R.string.wardrobe_season_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSeasonStore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SeasonItemRow(
    item: ClothingItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius.s))
            .clickable(onClick = onToggle)
            .padding(vertical = Dimens.Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        val image = item.displayImagePath
        if (image != null) {
            CmAsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Dimens.Radius.s))
                    .background(MaterialTheme.colorScheme.muted),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (item.isStored) R.string.wardrobe_season_item_stored else R.string.wardrobe_season_item_normal,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
        Text(
            text = item.code,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}

/** 衣物网格卡：抠图/原图铺 muted 底、左上编号 chip、「待完善」琥珀虚线 + ✨、「已收起」压暗 + 底条。 */
@Composable
private fun ClothingGridCard(
    item: ClothingItem,
    onClick: () -> Unit,
) {
    val itemLabel = buildString {
        append(item.displayName)
        append("，编号 ")
        append(item.code)
        if (item.isStored) append("，已收起")
        if (item.isIncomplete) append("，待完善")
    }
    val clickLabel = stringResource(
        if (item.isIncomplete) R.string.clothing_card_complete_action else R.string.clothing_card_detail_action,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Dimens.Radius.m))
            .background(MaterialTheme.colorScheme.muted)
            .semantics(mergeDescendants = true) { contentDescription = itemLabel }
            .clickable(onClickLabel = clickLabel, onClick = onClick),
    ) {
        // 抠图 PNG 直接铺 muted 浅底；M1 无抠图，走原图
        val image = item.displayImagePath
        // review U-a：§6.5 要求「已收起」卡「压暗 + 底条」两件套，原先只做了底条
        if (image != null) {
            CmAsyncImage(
                model = image,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = if (item.isStored) {
                    Modifier.fillMaxSize().sharedClothingImage(item.id).alpha(Dimens.StoredDimAlpha)
                } else {
                    Modifier.fillMaxSize().sharedClothingImage(item.id)
                },
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

        // 左上角编号 chip（surface 80% 底 + Label M）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimens.Space.s1)
                .clip(RoundedCornerShape(Dimens.Radius.s))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(horizontal = Dimens.Space.s2, vertical = Dimens.Space.s1),
        ) {
            Text(
                text = item.code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 「待完善」琥珀虚线 + ✨（§2.1 稀有高亮，每屏 ≤1 处；M1 无 AI，不算 AI 标识）
        if (item.isIncomplete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.Space.s1)
                    .dashedBorder(color = MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = Dimens.Space.s2, vertical = Dimens.Space.s1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Ai,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(Dimens.Icon.s),
                    )
                    Text(
                        text = stringResource(R.string.clothing_incomplete_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.accentText,
                        modifier = Modifier.padding(start = Dimens.Space.s1),
                    )
                }
            }
        }

        // 「已收起」压暗 + 底部条（§6.5）
        if (item.isStored) {
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
        }
    }
}

/** 琥珀虚线描边（「待完善」角标用，UI 文档 v1.6 §2.1 稀有高亮） */
private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color): Modifier =
    this.border(
        width = 1.dp,
        color = color,
        shape = RoundedCornerShape(Dimens.Radius.s),
    )

private fun WardrobeItemFilter.labelRes(): Int = when (this) {
    WardrobeItemFilter.All -> R.string.filter_all
    WardrobeItemFilter.Normal -> R.string.filter_normal
    WardrobeItemFilter.Stored -> R.string.filter_stored
    WardrobeItemFilter.Incomplete -> R.string.filter_incomplete
}

private fun WardrobeItemFilter.count(counts: WardrobeCounts): Int = when (this) {
    WardrobeItemFilter.All -> counts.total
    WardrobeItemFilter.Normal -> counts.normal
    WardrobeItemFilter.Stored -> counts.stored
    WardrobeItemFilter.Incomplete -> counts.incomplete
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonModeSheet(
    selectedSeasons: Set<String>,
    items: List<ClothingItem>,
    selectedIds: Set<Long>,
    normalCount: Int,
    storedCount: Int,
    selectedNormalCount: Int,
    selectedStoredCount: Int,
    onToggle: (String) -> Unit,
    onToggleItem: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRequestStore: () -> Unit,
    onRequestRestore: () -> Unit,
    onRequestSelectedStore: () -> Unit,
    onRequestSelectedRestore: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
        ) {
            Text(
                text = stringResource(R.string.wardrobe_season_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.wardrobe_season_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
            ChipGroup(
                options = Season.entries.map { ChipOption(it.key, it.displayName) },
                selectedKeys = selectedSeasons,
                onToggle = onToggle,
            )
            if (selectedSeasons.isEmpty()) {
                Text(
                    text = stringResource(R.string.wardrobe_season_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.wardrobe_season_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = items.all { it.id in selectedIds },
                        onCheckedChange = { checked -> onSelectAll(checked) },
                    )
                    Text(
                        text = stringResource(R.string.wardrobe_season_select_all, items.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s1),
                ) {
                    items(items, key = { it.id }) { item ->
                        SeasonItemRow(
                            item = item,
                            checked = item.id in selectedIds,
                            onToggle = { onToggleItem(item.id) },
                        )
                    }
                }
            }
            if (selectedNormalCount + selectedStoredCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                ) {
                    Button(
                        onClick = onRequestSelectedStore,
                        enabled = selectedNormalCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.wardrobe_season_store_selected, selectedNormalCount))
                    }
                    OutlinedButton(
                        onClick = onRequestSelectedRestore,
                        enabled = selectedStoredCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.wardrobe_season_restore_selected, selectedStoredCount))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
            ) {
                Button(
                    onClick = onRequestStore,
                    enabled = normalCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.wardrobe_season_store_count, normalCount))
                }
                OutlinedButton(
                    onClick = onRequestRestore,
                    enabled = storedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.wardrobe_season_restore_count, storedCount))
                }
            }
        }
    }
}
