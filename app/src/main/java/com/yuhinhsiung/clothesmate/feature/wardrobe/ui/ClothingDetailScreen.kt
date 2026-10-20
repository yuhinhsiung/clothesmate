package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.ColorDot
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.ClothingColors
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.accentText
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import kotlinx.coroutines.launch

/**
 * 衣物详情页 —— 开发计划 M1-11 / 开发文档 §2.1.2 W-05、UI 文档 §7.2、mockup-05。
 *
 * 顶部大图 →「No.102」大标题 + 名称 → 属性卡 → 底部操作（删除 / 收起 / 编辑）。
 * 删除按 W-13（M1-13）：软删除 + Snackbar「撤销」，撤销后完整复活。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun ClothingDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    onWardrobeClick: (Long) -> Unit = {},
    onOutfitClick: (Long) -> Unit = {},
    viewModel: ClothingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val item = uiState.item
    val deletedTemplate = stringResource(R.string.clothing_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val savedText = stringResource(R.string.clothing_saved)

    // 编辑页保存成功回执 →「已保存」Snackbar（M1-12 验收）
    val savedSignal by viewModel.savedSignal.collectAsStateWithLifecycle()
    LaunchedEffect(savedSignal) {
        if (savedSignal) {
            snackbarHostState.showSnackbar(savedText)
            viewModel.consumeSavedSignal()
        }
    }

    // 软删除成功后 Flow 会把 item 置空：此时若用户没点撤销，才退出页面
    LaunchedEffect(uiState.notFound) {
        if (uiState.notFound) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (item != null) {
                // 顶部大图：抠图置于 muted 底，M1 无抠图走原图。
                // review R-09：可双指放大 + 放大后平移；默认 Fit 让衣物整体可见。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .background(MaterialTheme.colorScheme.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = item.displayImagePath
                    if (image != null) {
                        ZoomableImage(
                            path = image,
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Wardrobe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onMuted,
                            modifier = Modifier.size(Dimens.Icon.l + Dimens.Space.s4),
                        )
                    }
                }

                // 编号大标题 + 名称
                Column(modifier = Modifier.padding(Dimens.pagePadding)) {
                    Text(
                        text = "No.${item.code}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(top = Dimens.Space.s1),
                    )
                }

                // 属性卡
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_category),
                    value = uiState.categoryLabel,
                )
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_color),
                    value = uiState.colorLabels
                        .mapNotNull { it.second?.displayName }
                        .joinToString("、")
                        .ifEmpty { "—" },
                    colorDots = uiState.colorLabels,
                )
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_style),
                    value = item.styleTags.joinToString("、").ifEmpty { "—" },
                )
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_season),
                    value = uiState.seasonLabels.joinToString("、").ifEmpty { "—" },
                )
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_wardrobe),
                    value = uiState.wardrobeLabel,
                    onClick = { onWardrobeClick(item.wardrobeId) },
                )
                // 穿着次数 + 最近穿着日期（review C-19：任务原文是「穿着次数·最近日期」）。
                // M1 无穿着记录，lastWornAt 恒 null，日期要 M3 接了 wear_record 才出现。
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_wear),
                    value = uiState.wearLabel + formatLastWorn(uiState.lastWornAt),
                )
                item.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                    AttributeCard(
                        label = stringResource(R.string.clothing_attr_brand),
                        value = brand,
                    )
                }
                item.note?.takeIf { it.isNotBlank() }?.let { note ->
                    AttributeCard(
                        label = stringResource(R.string.clothing_attr_note),
                        value = note,
                    )
                }

                // M4-03 一衣多穿：包含本衣物的穿搭，最近穿着优先（DAO 排序）。
                if (uiState.outfits.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.clothing_outfits_title, uiState.outfits.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            start = Dimens.pagePadding,
                            end = Dimens.pagePadding,
                            top = Dimens.Space.s5,
                            bottom = Dimens.Space.s2,
                        ),
                    )
                    uiState.outfits.forEach { outfit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOutfitClick(outfit.id) }
                                .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CmAsyncImage(
                                model = outfit.coverPath,
                                contentDescription = outfit.displayName,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.Radius.s)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = Dimens.Space.s3),
                            ) {
                                Text(
                                    text = outfit.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = outfit.lastWornAt?.let { formatLastWorn(it).removePrefix(" · ") }
                                        ?: stringResource(R.string.clothing_outfit_never),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onMuted,
                                )
                            }
                        }
                    }
                }

                // 底部操作：删除 / 收起 / 编辑
                // review UI-03：三个 TextButton 原先直接塞在固定 Row 里，360dp + 200% 字号下
                // 被挤到「编 / 辑」上下两行、图标与基线错位。改 FlowRow：放得下就一行，
                // 放不下**整组换行**，每个按钮始终保持自己的完整宽度，字号不缩。
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s1),
                ) {
                    TextButton(onClick = { showDelete = true }) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.clothing_action_delete),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = Dimens.Space.s1),
                        )
                    }
                    TextButton(onClick = viewModel::toggleStored) {
                        Icon(
                            AppIcons.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onMuted,
                        )
                        Text(
                            text = stringResource(
                                if (uiState.isStored) R.string.clothing_action_restore
                                else R.string.clothing_action_store
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = Dimens.Space.s1),
                        )
                    }
                    TextButton(onClick = { onEdit(item.id) }) {
                        Icon(AppIcons.Edit, contentDescription = null)
                        Text(
                            text = stringResource(R.string.clothing_action_edit),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = Dimens.Space.s1),
                        )
                    }
                }
            }
        }
    }

    if (showDelete && item != null) {
        // review R-10：打开弹窗前查一次引用数——「被 N 套穿搭引用，删除后这些穿搭中
        // 将显示『已删除』」是 W-13 规定的后果说明，原先弹窗只有一句固定文案
        LaunchedEffect(showDelete) { viewModel.loadOutfitRefCount() }
        ClothingDeleteDialog(
            code = item.code,
            referencedOutfitCount = uiState.outfitRefCount,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                val code = item.code
                viewModel.softDelete()
                // review C-08：软删后先弹「撤销」再退出（W-13「撤销后完整复活」）。
                // 不立即 onBack——撤销需要在同一 ViewModel 作用域内 restore。
                // 删除 Snackbar 文案在 Composable 作用域用 stringResource 求值
                // （lint LocalContextGetResourceValueCall：lambda 内读资源不感知配置变更）
                val deletedText = deletedTemplate.format(code)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedText,
                        actionLabel = undoLabel,
                    )
                    when (result) {
                        SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                        SnackbarResult.Dismissed -> onBack()
                    }
                }
            },
        )
    }
}

/**
 * 删除衣物确认（W-13）—— AlertDialog radius-l。
 *
 * review R-10：被穿搭引用时要按引用数说明后果（0 套不显示该句）。
 * 引用数在弹窗打开的瞬间取值；确认过程中数据变化（别处删掉了引用它的穿搭）
 * 只会让这句话偏保守，不影响删除语义。
 */
@Composable
private fun ClothingDeleteDialog(
    code: String,
    referencedOutfitCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(
                text = stringResource(R.string.clothing_delete_title, code),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.clothing_delete_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
                if (referencedOutfitCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.clothing_delete_refs_hint,
                            referencedOutfitCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.accentText,
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

/** 属性行：左标签（onMuted）+ 右值；可点时给视觉提示 */
@Composable
private fun AttributeCard(
    label: String,
    value: String,
    colorDots: List<Pair<String, ClothingColor?>> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    // review C-17：不固定 72×20dp——系统放大字号后固定宽会把标签截断。
    // 改用 widthIn(min) + 语义色区分「所属衣柜」这类可点行。
    val clickable = onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier
                        .background(MaterialTheme.colorScheme.muted)
                        .clickable { onClick?.invoke() }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier.widthIn(min = Dimens.AttrLabelMinWidth),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Dimens.Space.s3),
        ) {
            // 颜色行画色点（§6.5 颜色色块）。ClothingColor 只带 key/displayName，
            // 具体色值查 ClothingColors（UI 文档 §2.1 的 12 色 + 花色）。
            // 统一走 ColorDot：它自带「白色点加 outline 描边」的策略，
            // 自绘裸 Box 在浅底上白点看不见（视觉建议 4）。
            colorDots.forEach { (_, color) ->
                val dot = color?.let { ClothingColors.dotColor(it) }
                if (dot != null) {
                    ColorDot(
                        color = dot,
                        modifier = Modifier.padding(end = Dimens.Space.s1),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (clickable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/**
 * 最近穿着日期后缀（review C-19）。M1 无穿着记录（epoch millis 为 null），
 * 返回空串；M3 接了 wear_record 后有了值就格式化成「· 最近 M 月 d 日」。
 */
@Composable
private fun formatLastWorn(millis: Long?): String {
    if (millis == null) return ""
    val text = remember(millis) {
        java.text.SimpleDateFormat("M 月 d 日", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
    return " · 最近 $text"
}

/**
 * 可缩放详情大图（review R-09）。
 *
 * - 默认 [ContentScale.Fit]：宽图 / 长图 / 透明图都能**看到衣物整体**，
 *   不再像 [ContentScale.Crop] 那样把裁掉的区域永久藏起来；
 * - 双指缩放 1×–4×；放大后单指拖动平移，边界夹紧不拖出画面；
 * - 缩回 1× 自动回中。
 *
 * ## 与页面纵向滚动的关系（易踩）
 *
 * 详情页整体是 `verticalScroll`。`detectTransformGestures` 这类封装在单指拖动时
 * 也会消费事件，套上去页面就滚不动了。这里手写 `awaitEachGesture`：
 * **1× 且单指 → 完全不消费**（交给外层滚动）；双指、或已放大后的拖动才消费。
 *
 * 缩放/平移的**数值**全在 [ZoomState] 的纯函数里（JVM 可直测），本函数只管喂事件。
 */
@Composable
private fun ZoomableImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(path) { mutableStateOf(ZoomState()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multiTouch = event.changes.size > 1
                        if (multiTouch || zoom.isZoomed) {
                            zoom = zoom.applyGesture(zoomChange, panChange, containerSize)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CmAsyncImage(
            model = path,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
    }
}

/** 详情大图的缩放/平移状态 */
internal data class ZoomState(
    val scale: Float = MIN_ZOOM,
    val offset: Offset = Offset.Zero,
) {
    val isZoomed: Boolean get() = scale > MIN_ZOOM
}

/**
 * 一次手势事件后的新缩放状态（review R-09）——**纯函数**，JVM 可直测。
 *
 * 语义：
 * - 缩放夹在 [MIN_ZOOM]–[MAX_ZOOM]；
 * - 回到 1× 自动清零平移（否则缩小后再放大，画面会停在上次拖到的位置）；
 * - 平移夹紧在「放大溢出容器」的范围内，拖不出画面。
 */
internal fun ZoomState.applyGesture(
    zoomChange: Float,
    panChange: Offset,
    container: IntSize,
): ZoomState {
    val nextScale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    if (nextScale <= MIN_ZOOM) return ZoomState()
    return ZoomState(
        scale = nextScale,
        offset = clampOffset(offset + panChange, container, nextScale),
    )
}

/** 平移边界：放大 k 倍时最多能拖出容器的一半 */
internal fun clampOffset(raw: Offset, container: IntSize, scale: Float): Offset {
    if (container.width == 0 || container.height == 0) return Offset.Zero
    val maxX = container.width * (scale - 1f) / 2f
    val maxY = container.height * (scale - 1f) / 2f
    return Offset(
        x = raw.x.coerceIn(-maxX, maxX),
        y = raw.y.coerceIn(-maxY, maxY),
    )
}

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 4f
