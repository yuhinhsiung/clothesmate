package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 选择衣柜弹层 —— 开发计划 M1-08 / 开发文档 §12.1 #10、UI 文档 §7.2、mockup-18。
 *
 * 从确认衣物信息页（M1-14）点「所属衣柜」行唤起。底部固定「＋ 新建衣柜」次按钮，
 * 点击后叠加 [WardrobeEditSheet]（§7.2：选择衣柜弹层内新建）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobePickerSheet(
    onDismiss: () -> Unit,
    onPicked: (Wardrobe) -> Unit,
    viewModel: WardrobePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    remember(Unit) { viewModel.load(); null }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(
            topStart = Dimens.Radius.xl,
            topEnd = Dimens.Radius.xl,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s6),
        ) {
            // 顶部小横条（§6.8）
            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = Dimens.Space.s3),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onMuted),
                )
            }

            Text(
                text = stringResource(R.string.wardrobe_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = Dimens.Space.s3),
            )

            if (uiState.isEmpty) {
                // 空态：还没有衣柜 / 衣服总要有个家（§6.9 表格「选择衣柜弹层为空」）
                com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                    spec = EmptyStateSpec(
                        icon = AppIcons.Wardrobe,
                        title = stringResource(R.string.empty_picker_no_wardrobe_title),
                        description = stringResource(R.string.empty_picker_no_wardrobe_desc),
                        actionLabel = stringResource(R.string.wardrobe_add),
                        onAction = { showCreate = true },
                    ),
                )
            } else {
                WardrobePickerList(
                    wardrobes = uiState.wardrobes,
                    selectedId = uiState.selectedId,
                    onSelect = viewModel::onSelect,
                )
            }

            // 底部「＋ 新建衣柜」次按钮：有衣柜时常驻，无衣柜时上面空态已提供入口
            if (!uiState.isEmpty) {
                Button(
                    onClick = { showCreate = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(Dimens.Radius.m),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.Space.s4),
                ) {
                    Text(
                        text = stringResource(R.string.wardrobe_picker_create),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    if (showCreate) {
        WardrobeEditSheet(
            onDismiss = { showCreate = false },
            onSaved = { newId ->
                showCreate = false
                // 新建后直接选中新衣柜（§7.2：保存后自动选中并回传）
                viewModel.onSelect(newId)
                viewModel.confirm(onPicked)
            },
        )
    }
}
