package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.ShimmerCard
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣柜列表页 —— 开发计划 M1-05 / 开发文档 §2.1.2 W-03/W-08、UI 文档 §6.5/§7.2。
 */
@Composable
fun WardrobeRoute(
    onWardrobeClick: (Long) -> Unit = {},
    onShowAddSheet: () -> Unit = {},
    onEditWardrobe: (Long) -> Unit = {},
    onShowDelete: (Long) -> Unit = {},
    onAddClothing: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    viewModel: WardrobeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WardrobeListScreen(
        uiState = uiState,
        onWardrobeClick = onWardrobeClick,
        onAddWardrobe = onShowAddSheet,
        onEditWardrobe = onEditWardrobe,
        onWardrobeLongPress = onShowDelete,
        onAddClothing = onAddClothing,
        onOpenSearch = onOpenSearch,
        onPersistOrder = viewModel::persistOrder,
    )
}

/**
 * 衣柜列表页主体。
 *
 * review R-07：排序落库经 [onPersistOrder] 回调注入，而不在这里直接拿 ViewModel——
 * 拖拽手势是**交互逻辑**，必须能被 Compose UI 测试直接驱动（见 WardrobeDragReorderTest），
 * 而 `hiltViewModel()` 的默认参数会把它焊死在 Hilt 容器上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeListScreen(
    uiState: WardrobeListUiState,
    onWardrobeClick: (Long) -> Unit = {},
    onAddWardrobe: () -> Unit = {},
    onEditWardrobe: (Long) -> Unit = {},
    onWardrobeLongPress: (Long) -> Unit = {},
    onAddClothing: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onPersistOrder: (List<Long>) -> Unit = {},
) {
    // 拖拽重排时的本地顺序：松手后才经 ViewModel 落库，拖拽中只动 UI。
    // review B-13：重同步条件从「个数变化」改为「内容不一致」——改名/件数增减
    // （size 不变）时旧实现渲染过期对象，列表卡不随编辑刷新。
    // review R-07：与源快照比较（而不是和本地列表比），并加**拖拽守卫**——
    // 松手落库到 Flow 重发之间有窗口期，若此时按「本地 != 源」重同步，
    // 刚拖好的顺序会被旧顺序顶回去。
    val reordered = remember { mutableStateListOf<Wardrobe>() }
    var syncedSource by remember { mutableStateOf<List<Wardrobe>>(emptyList()) }
    val wardrobes = when (val s = uiState) {
        is WardrobeListUiState.Content -> s.wardrobes
        else -> emptyList()
    }

    // ── 长按拖拽排序（review R-07 / W-08）──────────────────────
    // 数据层（persistOrder）与 UI 骨架此前就在，缺的是网格卡上的手势与坐标重排。
    val gridState = rememberLazyGridState()
    /** 非空 = 正在拖这张卡（值为衣柜 id）。拖拽期间禁用上面的重同步。 */
    var draggingId by remember { mutableStateOf<Long?>(null) }
    /** 拖拽累计位移（相对当前格子）。换格子时按两格位移补偿，视觉上才连续 */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    if (draggingId == null && wardrobes != syncedSource) {
        reordered.clear()
        reordered.addAll(wardrobes)
        syncedSource = wardrobes
    }

    fun endDrag() {
        val dragged = draggingId
        draggingId = null
        dragOffset = Offset.Zero
        // 松手即持久化（W-08）；顺序由 UI 决定，Repository 只落库
        if (dragged != null && reordered.isNotEmpty()) {
            onPersistOrder(reordered.map { it.id })
        }
    }

    /**
     * 拖拽中：把「被拖卡片中心」落在哪个格子上算出来，跨格就换位。
     *
     * 坐标取自 [gridState] 的可见项布局（相对网格视口），所以滚动中同样成立。
     * 换位后按「原格 → 目标格」的位移**补偿** [dragOffset]，否则卡片会在换位瞬间
     * 跳出去一格的距离（格子跟着走、位移却没扣掉）。
     */
    fun onDrag(id: Long, dragAmount: Offset) {
        val fromIndex = reordered.indexOfFirst { it.id == id }
        if (fromIndex < 0) return
        val visible = gridState.layoutInfo.visibleItemsInfo
        val fromBounds = visible.firstOrNull { it.index == fromIndex } ?: return
        dragOffset += dragAmount
        val centerX = fromBounds.offset.x + fromBounds.size.width / 2f + dragOffset.x
        val centerY = fromBounds.offset.y + fromBounds.size.height / 2f + dragOffset.y
        val targetBounds = visible.firstOrNull { info ->
            centerX >= info.offset.x && centerX <= info.offset.x + info.size.width &&
                centerY >= info.offset.y && centerY <= info.offset.y + info.size.height
        } ?: return
        val target = targetBounds.index
        if (target == fromIndex || target !in reordered.indices) return
        dragOffset -= Offset(
            x = (targetBounds.offset.x - fromBounds.offset.x).toFloat(),
            y = (targetBounds.offset.y - fromBounds.offset.y).toFloat(),
        )
        reordered.add(target, reordered.removeAt(fromIndex))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.wardrobe_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                // review C-11：右上「+」= 新建衣柜（§6.2 动作图标位）；
                // FAB 按 §6.4 语义交还给「+ 录衣服」
                actions = {
                    IconButton(onClick = onAddWardrobe) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = stringResource(R.string.wardrobe_add),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            // §6.4：FAB「+ 录衣服」常驻右下。M1-14 未实装前点它有反馈，
            // 不留死按钮（review C-02）
            FloatingActionButton(
                onClick = onAddClothing,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = stringResource(R.string.clothing_add),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 常驻搜索框（§6.2 / W-07）：胶囊形 muted 底，点击进搜索结果二级页
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s2)
                    .height(Dimens.minTouchTarget)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.muted)
                    .clickable(onClick = onOpenSearch),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.Space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.size(Dimens.Icon.m),
                    )
                    Text(
                        text = stringResource(R.string.wardrobe_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(start = Dimens.Space.s2),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                            state = gridState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.pagePadding),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.waterfallColumnSpacing),
                            verticalArrangement = Arrangement.spacedBy(Dimens.waterfallColumnSpacing),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(
                                items = reordered,
                                key = { _, w -> w.id },
                            ) { _, wardrobe ->
                                val isDragging = draggingId == wardrobe.id
                                Box(
                                    modifier = Modifier
                                        // 被拖的卡浮到最上层，盖住相邻格子
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                                scaleX = 1.03f
                                                scaleY = 1.03f
                                            }
                                        }
                                        // 长按拖拽：见 [wardrobeDragToReorder]。
                                        // 这里不用现成的 detectDragGesturesAfterLongPress——
                                        // 它长按成立后只消费移动事件、**不消费抬指**，
                                        // 于是「长按后松手不拖」会被卡片自身的 clickable
                                        // 当成一次点击、顺手打开详情（本页 Compose 测试抓出）。
                                        .pointerInput(wardrobe.id) {
                                            wardrobeDragToReorder(
                                                onStart = {
                                                    draggingId = wardrobe.id
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragged = { amount ->
                                                    onDrag(wardrobe.id, amount)
                                                },
                                                onFinished = { endDrag() },
                                            )
                                        }
                                        // 非拖拽卡换位时补间动画；被拖的卡不能加，
                                        // 否则会和上面的手势位移叠加成二段跳
                                        .then(
                                            if (isDragging) {
                                                Modifier
                                            } else {
                                                Modifier.animateItem()
                                            },
                                        ),
                                ) {
                                    WardrobeCard(
                                        wardrobe = wardrobe,
                                        // 长按序列里的抬指会被卡片自身的 clickable 当成一次点击
                                        // （Main 传递自内向外，子节点先于本手势处理抬指，
                                        // 我们在父层再消费也来不及）——「长按后松手不拖」
                                        // 不该顺手打开详情，故在点击入口加一道拖拽守卫。
                                        onClick = {
                                            if (draggingId == null) onWardrobeClick(wardrobe.id)
                                        },
                                        onEdit = { onEditWardrobe(wardrobe.id) },
                                        onDelete = { onWardrobeLongPress(wardrobe.id) },
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

@Composable
private fun WardrobeCard(
    wardrobe: Wardrobe,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // 「更多」菜单展开状态：卡片本地持有，点外部/选项后自行收起
    var menuExpanded by remember { mutableStateOf(false) }

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
                        CmAsyncImage(
                            model = imageFile,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // 视觉建议 1：注释一直写着「muted 底 + 衣架图标占位」，
                        // 但这里实际没设背景——整张白卡只剩一个居中的衣架，
                        // 封面区与文字区没有分隔。补上 muted 底。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.muted),
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

            // 右上角「更多」→ 编辑 / 删除（review C-03：原先直连删除弹窗，
            // 没有菜单也没有编辑入口，W-02 一半落空）
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
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
                        text = { Text(stringResource(R.string.wardrobe_menu_edit)) },
                        leadingIcon = {
                            Icon(AppIcons.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
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
                            onDelete()
                        },
                    )
                }
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

/**
 * 「长按 → 拖动排序」手势（review R-07）。
 *
 * ## 为什么不直接用 `detectDragGesturesAfterLongPress`
 *
 * 卡片自身是 `Card(onClick)`（clickable 会消费 down），长按成立后它仍在等抬指。
 * 现成的手势检测器长按后只消费**移动**事件，抬指不消费——于是「长按后松手、没拖」
 * 会被卡片当成一次普通点击，顺手打开详情页（WardrobeDragReorderTest 抓出）。
 * 这里把长按后的**全部**事件（含抬指）一律消费：本手势一旦接管，就不该再有
 * 第二个消费者拿它做别的事。
 *
 * 长按**未**成立（普通点按）时什么都不消费，卡片的 clickable 照常工作。
 */
private suspend fun PointerInputScope.wardrobeDragToReorder(
    onStart: () -> Unit,
    onDragged: (Offset) -> Unit,
    onFinished: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        onStart()
        var pointer = longPress.id
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer }
            // 注意顺序：位移必须在 consume 之前取（positionChange() 在 change
            // 被消费后返回 Zero），抬指判定也要用「忽略消费」的版本
            val delta = if (change == null || (change.previousPressed && !change.pressed)) {
                null
            } else {
                change.position - change.previousPosition
            }
            event.changes.forEach { it.consume() }
            if (change == null || delta == null) break
            onDragged(delta)
            pointer = change.id
        }
        onFinished()
    }
}
