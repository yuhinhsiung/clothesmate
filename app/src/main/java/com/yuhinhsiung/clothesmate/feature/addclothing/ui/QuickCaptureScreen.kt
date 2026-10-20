package com.yuhinhsiung.clothesmate.feature.addclothing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.OnPhoto
import com.yuhinhsiung.clothesmate.ui.theme.PhotoScrim
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 快速连拍 —— M4-02 / 开发文档 §2.2.4。
 *
 * 拍摄阶段复用 [CaptureScreen] 的取景与权限降级；每张照片立即建档发号，
 * 顶部显示已拍数量、底部胶片条可删除单张。结束后进入统一提交页，
 * 抠图与取色在 [QuickCaptureViewModel] 的应用级后台继续。
 */
@Composable
fun QuickCaptureRoute(
    onBack: () -> Unit,
    onDone: (Long) -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickCaptureEvent.Done -> onDone(event.wardrobeId)
                is QuickCaptureEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.wardrobe == null -> WardrobePicker(
                wardrobes = state.wardrobes,
                onBack = onBack,
                onSelect = viewModel::selectWardrobe,
            )

            state.capturing -> QuickCaptureCamera(
                state = state,
                onBack = viewModel::endSession,
                onCaptured = viewModel::capture,
                onDelete = viewModel::deleteShot,
                onEnd = viewModel::endSession,
            )

            else -> QuickCaptureReview(
                state = state,
                onBack = onBack,
                onResume = viewModel::resumeCapture,
                onDone = viewModel::complete,
                onDelete = viewModel::deleteShot,
                onAcceptAiSuggestion = viewModel::acceptAiSuggestion,
            )
        }
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun WardrobePicker(
    wardrobes: List<Wardrobe>,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(Dimens.pagePadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, stringResource(R.string.quick_capture_close))
                }
                Text(
                    stringResource(R.string.quick_capture_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(Dimens.Space.s5))
            Text(
                stringResource(R.string.quick_capture_choose_wardrobe),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            if (wardrobes.isEmpty()) {
                Text(
                    stringResource(R.string.quick_capture_no_wardrobe),
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                ) {
                    items(wardrobes, key = { it.id }) { wardrobe ->
                        OutlinedButton(
                            onClick = { onSelect(wardrobe.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(wardrobe.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCaptureCamera(
    state: QuickCaptureUiState,
    onBack: () -> Unit,
    onCaptured: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onEnd: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        CaptureScreen(onBack = onBack, onCaptured = onCaptured)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Dimens.Space.s2),
            color = PhotoScrim,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                stringResource(R.string.quick_capture_count, state.total),
                modifier = Modifier.padding(
                    horizontal = Dimens.Space.s4,
                    vertical = Dimens.Space.s2,
                ),
                color = OnPhoto,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    bottom = 142.dp,
                    start = Dimens.Space.s4,
                    end = Dimens.Space.s2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            ) {
                items(state.shots, key = { it.id }) { shot ->
                    Box(Modifier.size(52.dp)) {
                        CmAsyncImage(
                            model = shot.previewPath,
                            contentDescription = stringResource(
                                R.string.quick_capture_shot,
                                shot.code,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(Dimens.Radius.s)),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = { onDelete(shot.id) },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(PhotoScrim, RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        AppIcons.Close,
                                        contentDescription = stringResource(R.string.quick_capture_delete),
                                        tint = OnPhoto,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        if (shot.processing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onEnd, enabled = state.total > 0) {
                Text(
                    if (state.ending) stringResource(R.string.quick_capture_processing)
                    else stringResource(R.string.quick_capture_end),
                    color = OnPhoto,
                )
            }
        }
    }
}
