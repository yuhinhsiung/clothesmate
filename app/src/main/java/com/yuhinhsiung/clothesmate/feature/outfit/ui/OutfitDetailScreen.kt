package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.OutfitDetail
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitDetailArgs
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 穿搭详情页 —— 开发计划 M1-20 / 开发文档 §2.3.4、UI 文档 §7.4、mockup-09。
 *
 * 照片轮播（多张 + 页码 dots + n/m；单张 / 无照片不显示指示器）→ 标题 → 所属分类 →
 * 全部标签 → 穿着次数 → 衣物清单（**已删除衣物 = 「已删除」占位卡，保留原编号、不可点击**）
 * → 底部主按钮「今天穿这套」（M1 置灰，M3-06 实装）→ 更多菜单 = 编辑 / 删除。
 * 删除二次确认；含已删除衣物时追加「将同时彻底清除 N 件已删除衣物」（W-13）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onItemDetailClick: (Long) -> Unit,
    viewModel: OutfitDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail

    var menuExpanded by remember { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.outfit_deleted_snackbar)
    // 编辑保存回执 → Snackbar（与衣物编辑同模式）
    val savedSignal by viewModel.savedSignal.collectAsStateWithLifecycle()
    val savedText = stringResource(R.string.clothing_saved)
    LaunchedEffect(savedSignal) {
        if (savedSignal) {
            snackbarHostState.showSnackbar(savedText)
            viewModel.consumeSavedSignal()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(Dimens.Space.s3),
                    )
                },
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
                                text = { Text(stringResource(R.string.clothing_action_edit)) },
                                leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    detail?.let { onEdit(it.outfit.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_delete),
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
                                    showDelete = true
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (detail == null) {
            if (!uiState.notFound) {
                // 加载骨架（§6.9：列表加载不用转圈）
                Column(modifier = Modifier.padding(innerPadding).padding(Dimens.pagePadding)) {
                    repeat(3) {
                        com.yuhinhsiung.clothesmate.ui.component.ShimmerCard(height = 120.dp)
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 照片轮播 ────────────────────────────────────────
            val photos = detail.outfit.photoPaths
            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f)
                        .background(MaterialTheme.colorScheme.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Outfit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.size(Dimens.Icon.l + Dimens.Space.s4),
                    )
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { photos.size })
                Box {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 5f),
                    ) { page ->
                        CmAsyncImage(
                            model = photos[page],
                            contentDescription = detail.outfit.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (photos.size > 1) {
                        // 右下页码 dots + n/m（单张不显示，§7.4）
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Dimens.Space.s3)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = Dimens.Space.s2, vertical = Dimens.Space.s1),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s1),
                        ) {
                            repeat(photos.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == pagerState.currentPage) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onMuted
                                            },
                                        ),
                                )
                            }
                            Text(
                                text = "${pagerState.currentPage + 1}/${photos.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = Dimens.Space.s1),
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(Dimens.pagePadding)) {
                // 标题
                Text(
                    text = detail.outfit.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                // 所属分类 chips（可能多个，§2.3.4）
                if (detail.categories.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.outfit_detail_categories))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
                        itemsIndexed(detail.categories) { _, category ->
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = Dimens.Space.s3, vertical = Dimens.Space.s1),
                            )
                        }
                    }
                }

                // 全部标签（风格 / 场景 / 季节，§2.3.4）
                if (detail.outfit.styleTags.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.form_section_style))
                    TagChipsRow(detail.outfit.styleTags)
                }
                if (detail.outfit.sceneTags.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.outfit_section_scene))
                    TagChipsRow(detail.outfit.sceneTags)
                }
                if (detail.outfit.seasonTags.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.form_section_season))
                    TagChipsRow(detail.outfit.seasonTags.mapNotNull { Season.fromKey(it)?.displayName })
                }

                // 穿着次数（M1 无穿着记录 → 优雅降级，与衣物详情同口径）
                SectionLabel(stringResource(R.string.outfit_detail_wear))
                Text(
                    text = when (detail.outfit.wearCount) {
                        0 -> stringResource(R.string.outfit_wear_zero)
                        1 -> stringResource(R.string.outfit_wear_one)
                        else -> stringResource(R.string.outfit_wear_n, detail.outfit.wearCount)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // ── 衣物清单 · n 件 ─────────────────────────────
                SectionLabel(stringResource(R.string.outfit_detail_items, detail.items.size))
                detail.items.forEach { ref ->
                    OutfitItemRow(
                        ref = ref,
                        onClick = { if (!ref.deleted) onItemDetailClick(ref.itemId) },
                    )
                }

                // ── 「今天穿这套」（M1 置灰，M3-06 实装） ──────────
                Button(
                    onClick = { },
                    enabled = false,
                    shape = RoundedCornerShape(Dimens.Radius.m),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.Space.s6)
                        .height(Dimens.buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary
                            .copy(alpha = Dimens.DisabledAlpha),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            .copy(alpha = Dimens.DisabledAlpha),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.outfit_wear_today),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    if (showDelete && detail != null) {
        // review B-10：弹窗 N 用「独属本穿搭才计入」的预计算口径（countClearableDeletedItems），
        // 而非本穿搭内全部软删件数——软删衣物同属多套穿搭时原文案失实
        LaunchedEffect(showDelete) { viewModel.loadClearableCount() }
        OutfitDeleteDialog(
            outfitName = detail.outfit.displayName,
            deletedItemCount = uiState.clearableCount,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                // review B-17：删除失败不再裸崩，落 Snackbar 供重试
                viewModel.delete(
                    onDone = { cleared ->
                        // 删除后弹层消失、列表自动刷新（Flow）；被清衣物数仅记录
                        scope.launch {
                            snackbarHostState.showSnackbar(deletedText.format(detail.outfit.displayName))
                        }
                        onBack()
                    },
                    onError = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            },
        )
    }
}

/** 衣物清单行：缩略图 + 名称 + No. + 所属衣柜；已删除 = 占位卡（W-13）。 */
@Composable
private fun OutfitItemRow(
    ref: com.yuhinhsiung.clothesmate.core.model.OutfitItemRef,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space.s2)
            .then(if (ref.deleted) Modifier else Modifier.clickable(onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.Space.s8)
                .clip(RoundedCornerShape(Dimens.Radius.s))
                .background(MaterialTheme.colorScheme.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (ref.deleted) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier
                        .size(Dimens.Icon.m)
                        .alpha(Dimens.StoredDimAlpha),
                )
            } else if (ref.imagePath != null) {
                CmAsyncImage(
                    model = ref.imagePath,
                    contentDescription = ref.displayName,
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
            if (ref.deleted) {
                Text(
                    text = stringResource(R.string.outfit_item_deleted),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else {
                Text(
                    text = ref.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                // 占位卡同样保留原编号（W-13）
                text = "No.${ref.code}" +
                    (ref.wardrobeName?.takeIf { !ref.deleted }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onMuted,
        modifier = Modifier.padding(top = Dimens.Space.s4, bottom = Dimens.Space.s1),
    )
}

@Composable
private fun TagChipsRow(tags: List<String>) {
    if (tags.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
        itemsIndexed(tags) { _, tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.muted)
                    .padding(horizontal = Dimens.Space.s3, vertical = Dimens.Space.s1),
            )
        }
    }
}

/** 删除穿搭二次确认（§6.8）；含已删除衣物追加「将同时彻底清除 N 件已删除衣物」。 */
@Composable
private fun OutfitDeleteDialog(
    outfitName: String,
    deletedItemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(
                text = stringResource(R.string.outfit_delete_title, outfitName),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.outfit_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
                if (deletedItemCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.outfit_delete_cleared_hint,
                            deletedItemCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Dimens.Space.s2),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * 穿搭详情 ViewModel —— M1-20 / review B-03、B-10、B-17 修复落点。
 */
@HiltViewModel
class OutfitDetailViewModel @Inject constructor(
    private val outfitRepository: com.yuhinhsiung.clothesmate.feature.outfit.data.OutfitRepository,
    private val imageStorage: com.yuhinhsiung.clothesmate.core.image.ImageStorage,
    private val navResultBus: com.yuhinhsiung.clothesmate.ui.navigation.NavResultBus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val outfitId: Long = savedStateHandle.toRoute<OutfitDetailArgs>().outfitId

    /** 删除弹窗用的「将同时彻底清除 N 件」预计算值（B-10 口径：独属本穿搭才计入） */
    private val clearableCount = MutableStateFlow(0)

    val uiState: StateFlow<OutfitDetailUiState> = combine(
        outfitRepository.observeOutfitDetail(outfitId),
        clearableCount,
    ) { detail, count ->
        when {
            detail == null -> OutfitDetailUiState(notFound = true)
            else -> OutfitDetailUiState(detail = detail, clearableCount = count)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutfitDetailUiState())

    /** 编辑保存回执（经 NavResultBus，review B-04 同根因修复） */
    private val _savedSignal = MutableStateFlow(false)
    val savedSignal: StateFlow<Boolean> = _savedSignal.asStateFlow()

    init {
        viewModelScope.launch {
            navResultBus.outfitSaved().collect { saved ->
                if (saved) _savedSignal.value = true
            }
        }
    }

    fun consumeSavedSignal() {
        _savedSignal.value = false
        navResultBus.consumeOutfitSaved()
    }

    /**
     * 删除弹窗展示时预计算「将随本穿搭一并彻底清除」的软删衣物数（review B-10：
     * 用 `countClearableDeletedItems`（独属本穿搭才计入）而非全部软删件数，
     * 否则软删衣物同属多套穿搭时文案失实）。
     */
    fun loadClearableCount() {
        viewModelScope.launch {
            clearableCount.value = outfitRepository.countClearableDeletedItems(outfitId)
        }
    }

    /**
     * 删除穿搭（W-13）。**图片清理在本层接线**（review B-03：Repository 只删行与
     * 关联、经 `onDeleted` 回调通知 UI 层清文件——此前回调从未被传入，照片与被连带
     * 清除衣物的图片永久留存）。失败经 runCatching 捕获并回传错误（review B-17），
     * 不让 DB 异常裸崩。
     *
     * @param onDone 删除成功回调（参数 = 被连带物理清除的软删衣物件数）
     * @param onError 删除失败回调（展示可重试的错误反馈）
     */
    fun delete(onDone: suspend (Int) -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                outfitRepository.deleteOutfit(
                    outfitId,
                    onDeleted = { deletedOutfitId, clearedItemIds ->
                        // 穿搭照片目录整删 + 被连带清除衣物的原图/抠图（§3.3 删除口径表）
                        imageStorage.deleteOutfitImages(deletedOutfitId)
                        clearedItemIds.forEach { itemId ->
                            imageStorage.deleteClothingImages(itemId)
                        }
                    },
                )
            }.onSuccess { cleared ->
                onDone(cleared)
            }.onFailure { failure ->
                onError(failure.message ?: "删除失败，请重试")
            }
        }
    }
}

/** 穿搭详情 UI 状态 */
data class OutfitDetailUiState(
    val detail: OutfitDetail? = null,
    val notFound: Boolean = false,
    /** 删除弹窗用的「将同时彻底清除 N 件」预计算值（B-10 口径：独属本穿搭才计入） */
    val clearableCount: Int = 0,
)
