package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 删除衣柜去向弹层 —— 开发计划 M1-07 / 开发文档 W-12、UI 文档 §7.2、mockup-20。
 *
 * AlertDialog radius-l；标题「删除『{名称}』？」；说明「内有 {n} 件衣物，删除前请选择处理方式」。
 * 两个单选：①转移到其他衣柜（默认，行内复用选择衣柜组件）②一并删除 {n} 件衣物（error 色）。
 * 按钮：[取消] 文字按钮 · [确认删除] error 主按钮。
 */
@Composable
fun WardrobeDeleteDialog(
    wardrobeId: Long,
    onDismiss: () -> Unit,
    onDeleted: (DeleteResult) -> Unit,
    viewModel: WardrobeDeleteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    remember(wardrobeId) { viewModel.load(wardrobeId); null }

    val wardrobeName = uiState.wardrobe?.name.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(
                text = stringResource(R.string.wardrobe_delete_title, wardrobeName),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.wardrobe_delete_subtitle, uiState.itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )

                // ① 转移到其他衣柜（默认选中；无其他衣柜时禁用）
                DestinyOption(
                    selected = uiState.transferSelected,
                    enabled = uiState.canTransfer,
                    title = stringResource(R.string.wardrobe_delete_transfer),
                    onClick = { viewModel.onDestinyChange(true) },
                ) {
                    if (uiState.targets.isEmpty()) {
                        Text(
                            text = stringResource(R.string.wardrobe_delete_no_target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onMuted,
                        )
                    } else {
                        // 行内复用选择衣柜组件指定目标
                        Column {
                            uiState.targets.forEach { target ->
                                WardrobeRadioRow(
                                    wardrobe = target,
                                    selected = target.id == uiState.selectedTargetId,
                                    onSelect = { viewModel.onTargetChange(target.id) },
                                )
                            }
                        }
                    }
                }

                // ② 一并删除（error 色 + 不可恢复警示）
                DestinyOption(
                    selected = !uiState.transferSelected,
                    enabled = true,
                    title = stringResource(R.string.wardrobe_delete_all, uiState.itemCount),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { viewModel.onDestinyChange(false) },
                ) {
                    Text(
                        text = stringResource(R.string.wardrobe_delete_irreversible),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                uiState.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Dimens.Space.s3),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.confirm(onDeleted) },
                enabled = uiState.canConfirm,
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
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

/** 单选选项行：标题 + 下方展开的附加内容（转移目标列表 / 警示文案）。 */
@Composable
private fun DestinyOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    onClick: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.Space.s4),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) titleColor else MaterialTheme.colorScheme.onMuted,
            )
            if (selected) {
                Column(modifier = Modifier.padding(top = Dimens.Space.s2)) {
                    content()
                }
            }
        }
    }
}
