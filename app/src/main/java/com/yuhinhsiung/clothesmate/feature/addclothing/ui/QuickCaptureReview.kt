package com.yuhinhsiung.clothesmate.feature.addclothing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.OnPhoto
import com.yuhinhsiung.clothesmate.ui.theme.PhotoScrim
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/** 连拍结束后的统一提交页；处理中的缩略图显示进度，编号已发放可稍后补录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureReview(
    state: QuickCaptureUiState,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onDone: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_capture_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, stringResource(R.string.quick_capture_close))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Dimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
            ) {
                OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.quick_capture_continue))
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    enabled = state.shots.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.quick_capture_done))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.pagePadding),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(Dimens.Radius.m),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val range = state.codeRange
                Text(
                    if (range != null) {
                        stringResource(
                            R.string.quick_capture_info,
                            state.shots.size,
                            range.substringBefore('-'),
                            range.substringAfter('-', range),
                        )
                    } else {
                        stringResource(R.string.quick_capture_empty)
                    },
                    modifier = Modifier.padding(Dimens.Space.s3),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(Dimens.Space.s3))
            if (state.shots.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.quick_capture_empty),
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = Dimens.Space.s4),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                ) {
                    items(state.shots, key = { it.id }) { shot ->
                        Box {
                            CmAsyncImage(
                                model = shot.previewPath,
                                contentDescription = stringResource(
                                    R.string.quick_capture_shot,
                                    shot.code,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(152.dp)
                                    .clip(RoundedCornerShape(Dimens.Radius.s)),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                "No.${shot.code}",
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(Dimens.Space.s1)
                                    .background(PhotoScrim, RoundedCornerShape(Dimens.Radius.s))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = OnPhoto,
                                style = MaterialTheme.typography.labelSmall,
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
                                            .size(32.dp)
                                            .background(PhotoScrim, RoundedCornerShape(50)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            AppIcons.Close,
                                            contentDescription = stringResource(R.string.quick_capture_delete),
                                            tint = OnPhoto,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            if (shot.processing) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.28f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = OnPhoto,
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
