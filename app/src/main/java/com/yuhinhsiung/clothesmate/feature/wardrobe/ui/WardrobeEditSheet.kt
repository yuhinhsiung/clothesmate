package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 添加 / 编辑衣柜 BottomSheet —— 开发计划 M1-06 / 开发文档 W-01/W-02、UI 文档 §7.2。
 *
 * 三种入口共用：列表右上「+」、空态按钮、选择衣柜弹层内「＋ 新建衣柜」（M1-08）。
 *
 * @param wardrobeId 非空 = 编辑该衣柜（预填）；null = 新建
 * @param onDismiss 关闭弹层
 * @param onSaved 保存成功，参数为衣柜 id（新建链路供调用方「自动选中并回传」）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeEditSheet(
    wardrobeId: Long? = null,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: WardrobeEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Photo Picker：不申请存储权限，返回的 Uri 授权随本进程有效
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let(viewModel::onCoverPicked)
    }

    remember(wardrobeId) {
        if (wardrobeId != null) viewModel.loadForEdit(wardrobeId)
        null
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onDismiss()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        // radius-xl 顶圆角（§6.8）
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = Dimens.Radius.xl,
            topEnd = Dimens.Radius.xl,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s6),
        ) {
            // 顶部小横条（§6.8）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = Dimens.Space.s3)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onMuted),
            )

            Text(
                text = stringResource(
                    if (uiState.isEditing) R.string.wardrobe_edit_title
                    else R.string.wardrobe_new_title
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = Dimens.Space.s3),
            )

            // 封面：虚线占位 + 拍照/相册（§7.2）
            CoverPicker(
                coverPath = uiState.coverPath,
                compressing = uiState.compressing,
                onPick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClear = viewModel::onClearCover,
            )

            // 名称：必填，实时非空校验，错误就近提示
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.wardrobe_field_name)) },
                placeholder = { Text("主卧大衣柜") },
                isError = uiState.showNameError,
                supportingText = if (uiState.showNameError) {
                    { Text(stringResource(R.string.wardrobe_error_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.Radius.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s4),
            )

            // 备注：可选
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.wardrobe_field_note)) },
                placeholder = { Text("位置描述，如「朝南 · 阳台侧」") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.Radius.s),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Dimens.Space.s3),
                )
            }

            // 主按钮「保存衣柜」：名称为空时禁用（§7.2）
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = uiState.canSave,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.Radius.m),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.buttonHeight)
                    .padding(top = Dimens.Space.s5),
                // 禁用态靠 alpha 38%（§6.3）
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (uiState.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.Icon.m),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wardrobe_save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** 封面上传区：虚线占位卡；已有封面时显示图 + 右上角移除。 */
@Composable
private fun CoverPicker(
    coverPath: String?,
    compressing: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.Radius.m))
            .dashedBorder()
            .clickable(enabled = !compressing, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = coverPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            // 换封面：右上角圆形关闭钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.Space.s2)
                    .size(Dimens.minTouchTarget / 2)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.wardrobe_cover_remove),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.Icon.s),
                )
            }
        } else if (compressing) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.Icon.l),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = AppIcons.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(Dimens.Icon.l),
                )
                Text(
                    text = stringResource(R.string.wardrobe_cover_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.padding(top = Dimens.Space.s2),
                )
            }
        }
    }
}

/** 虚线描边（封面占位用，§7.2「虚线占位卡」）。四边完整，色取 token 的 outline。 */
@Composable
private fun Modifier.dashedBorder(): Modifier {
    val outlineColor = MaterialTheme.colorScheme.outline
    return this.drawBehind {
        val stroke = 1.dp.toPx()
        val dash = 6.dp.toPx()
        val effect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
        // 四条边各自画一段：虚线从角上起笔会重合，故内缩半个 dash
        val inset = dash / 2
        drawLine(
            color = outlineColor,
            start = Offset(inset, stroke),
            end = Offset(size.width - inset, stroke),
            strokeWidth = stroke,
            pathEffect = effect,
        )
        drawLine(
            color = outlineColor,
            start = Offset(inset, size.height - stroke),
            end = Offset(size.width - inset, size.height - stroke),
            strokeWidth = stroke,
            pathEffect = effect,
        )
        drawLine(
            color = outlineColor,
            start = Offset(stroke, inset),
            end = Offset(stroke, size.height - inset),
            strokeWidth = stroke,
            pathEffect = effect,
        )
        drawLine(
            color = outlineColor,
            start = Offset(size.width - stroke, inset),
            end = Offset(size.width - stroke, size.height - inset),
            strokeWidth = stroke,
            pathEffect = effect,
        )
    }
}
