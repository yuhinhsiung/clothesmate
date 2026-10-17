package com.yuhinhsiung.clothesmate.feature.addclothing.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.ShimmerCard
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import kotlin.math.abs

/**
 * 抠图预览页 —— M2-01 / UI 文档 §7.3、mockup-14。
 *
 * 处理中：shimmer + 「抠图处理中…」；完成后顶部分段控件「抠图效果 / 原图」，
 * 抠图铺在棋盘格（透明）上；底部 [重拍] / [用原图] / [确认]。
 * 抠图失败时只剩 [重拍] / [用原图]——**绝不阻断录入**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoutPreviewRoute(
    onBack: () -> Unit,
    onConfirm: (cutoutPath: String) -> Unit,
    onUseOriginal: () -> Unit,
    onRetake: () -> Unit,
    viewModel: CutoutPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.cutout_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s2),
        ) {
            when (state.phase) {
                CutoutPhase.Processing -> {
                    ShimmerCard(height = 320.dp, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.cutout_processing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.Space.s4),
                        textAlign = TextAlign.Center,
                    )
                }

                CutoutPhase.Ready -> {
                    TwoSegmentControl(
                        left = stringResource(R.string.cutout_segment_cutout),
                        right = stringResource(R.string.cutout_segment_original),
                        selectedLeft = state.mode == CutoutPreviewMode.Cutout,
                        onSelectLeft = { viewModel.onModeChange(CutoutPreviewMode.Cutout) },
                        onSelectRight = { viewModel.onModeChange(CutoutPreviewMode.Original) },
                    )
                    PreviewBox(
                        cutoutPath = state.cutoutPath,
                        originalUri = viewModel.sourceUri,
                        showCutout = state.mode == CutoutPreviewMode.Cutout,
                        onToggleMode = viewModel::toggleMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.Space.s4),
                    )
                    Text(
                        text = stringResource(R.string.cutout_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.Space.s3),
                        textAlign = TextAlign.Center,
                    )
                }

                CutoutPhase.Failed -> {
                    FailedCard(
                        error = state.error,
                        onRetry = viewModel::process,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.Space.s5),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s5, bottom = Dimens.Space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            ) {
                TextButton(onClick = { viewModel.retake(onRetake) }) {
                    Text(
                        text = stringResource(R.string.cutout_retake),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = { viewModel.useOriginal(onUseOriginal) },
                    shape = RoundedCornerShape(Dimens.Radius.m),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.buttonHeight),
                ) {
                    Text(
                        text = stringResource(R.string.cutout_use_original),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = { viewModel.confirm(onConfirm) },
                    enabled = state.phase == CutoutPhase.Ready && state.cutoutPath != null,
                    shape = RoundedCornerShape(Dimens.Radius.m),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary
                            .copy(alpha = Dimens.DisabledAlpha),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            .copy(alpha = Dimens.DisabledAlpha),
                    ),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(Dimens.buttonHeight),
                ) {
                    Text(
                        text = stringResource(R.string.cutout_confirm),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** 分段控件（抠图效果 / 原图）—— muted 底 + 选中 surface 胶囊。 */
@Composable
private fun TwoSegmentControl(
    left: String,
    right: String,
    selectedLeft: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.settingsSegmentHeight)
            .background(
                color = MaterialTheme.colorScheme.muted,
                shape = RoundedCornerShape(Dimens.Radius.m),
            )
            .padding(Dimens.Space.s1),
    ) {
        Segment(label = left, selected = selectedLeft, onClick = onSelectLeft)
        Segment(label = right, selected = !selectedLeft, onClick = onSelectRight)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(Dimens.Radius.s + Dimens.Space.s1),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onMuted
            },
        )
    }
}

/** 预览框：抠图模式铺棋盘格（表达透明），原图模式铺 muted 底；左右滑动切换。 */
@Composable
private fun PreviewBox(
    cutoutPath: String?,
    originalUri: String,
    showCutout: Boolean,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkerLight = MaterialTheme.colorScheme.surface
    val checkerDark = MaterialTheme.colorScheme.muted
    val cell = Dimens.checkerCell
    val dragThreshold = with(LocalDensity.current) { Dimens.Space.s8.toPx() }
    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(Dimens.Radius.l))
            .then(
                if (showCutout) {
                    Modifier.drawBehind {
                        val cellPx = cell.toPx()
                        drawRect(checkerLight)
                        var row = 0
                        var y = 0f
                        while (y < size.height) {
                            var col = 0
                            var x = 0f
                            while (x < size.width) {
                                if ((row + col) % 2 == 1) {
                                    drawRect(
                                        color = checkerDark,
                                        topLeft = Offset(x, y),
                                        size = Size(cellPx, cellPx),
                                    )
                                }
                                x += cellPx
                                col++
                            }
                            y += cellPx
                            row++
                        }
                    }
                } else {
                    Modifier.background(MaterialTheme.colorScheme.muted)
                },
            )
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                    onDragEnd = { if (abs(total) > dragThreshold) onToggleMode() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showCutout && cutoutPath != null) {
            CmAsyncImage(
                model = cutoutPath,
                contentDescription = stringResource(R.string.cutout_segment_cutout),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CmAsyncImage(
                model = originalUri,
                contentDescription = stringResource(R.string.cutout_segment_original),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FailedCard(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.Radius.l))
            .background(MaterialTheme.colorScheme.muted)
            .padding(Dimens.Space.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.emptyStateCircle - Dimens.Space.s6)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.Ai,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.size(Dimens.Icon.l),
            )
        }
        Text(
            text = stringResource(R.string.cutout_failed_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Dimens.Space.s4),
        )
        Text(
            text = stringResource(R.string.cutout_failed_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier.padding(top = Dimens.Space.s2),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.cutout_retry),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
