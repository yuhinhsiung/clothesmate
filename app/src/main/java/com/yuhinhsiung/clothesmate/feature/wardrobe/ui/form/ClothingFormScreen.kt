package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.ui.component.ChipGroup
import com.yuhinhsiung.clothesmate.ui.component.ChipOption
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.component.DiscardChangesDialog
import com.yuhinhsiung.clothesmate.ui.component.SelectableChip
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣物表单页 —— M1-14 录入确认页 / M1-12 编辑页（复用同一表单，开发计划 M1-12「复用确认页表单」）。
 *
 * 照片居顶 → 类别（两级 chips）→ 主颜色（12 色点选）→ 风格（联想 + 自定义）→
 * 适合季节 → 品牌 / 备注 → 所属衣柜行（唤起选择衣柜弹层，C-01 入口验证）→ 编号（自动生成
 * 可改 + 实时唯一性校验）→ 主按钮「保存」。
 *
 * M1-15：有已填写内容时系统返回弹「放弃本次编辑？」；拍照页返回 = 直接放弃（在 Capture 页侧）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingFormRoute(
    onBack: () -> Unit,
    onSavedCreate: (wardrobeId: Long) -> Unit,
    onSavedEdit: () -> Unit,
    viewModel: ClothingFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val styleSuggestions by viewModel.styleSuggestions.collectAsStateWithLifecycle()

    var showWardrobePicker by rememberSaveable { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showCustomTag by remember { mutableStateOf(false) }

    // 编辑目标不存在（已删除等）：直接退回
    LaunchedEffect(uiState.notFound) {
        if (uiState.notFound) onBack()
    }

    // M1-15：有已填写内容时返回先确认
    BackHandler(enabled = uiState.userModified && !uiState.saving) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEdit) R.string.clothing_form_title_edit
                            else R.string.clothing_form_title_add,
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier
                            .clickable {
                                if (uiState.userModified) showDiscard = true else onBack()
                            }
                            .padding(Dimens.Space.s3),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        ClothingFormContent(
            state = uiState,
            styleSuggestions = styleSuggestions,
            onCategoryChange = viewModel::onCategoryChange,
            onColorToggle = viewModel::onColorToggle,
            onStyleToggle = viewModel::onStyleToggle,
            onSeasonToggle = viewModel::onSeasonToggle,
            onBrandChange = viewModel::onBrandChange,
            onNoteChange = viewModel::onNoteChange,
            onCodeChange = viewModel::onCodeChange,
            onWardrobeRowClick = { showWardrobePicker = true },
            onAddCustomStyle = { showCustomTag = true },
            onSave = {
                viewModel.save { wardrobeId, isEdit ->
                    if (isEdit) onSavedEdit() else onSavedCreate(wardrobeId)
                }
            },
            modifier = Modifier
                .padding(innerPadding)
                .imePadding(),
        )
    }

    // C-01 入口验证：确认页点「所属衣柜」行 → 选择衣柜弹层，带上当前已选
    if (showWardrobePicker) {
        com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobePickerSheet(
            onDismiss = { showWardrobePicker = false },
            onPicked = { wardrobe ->
                showWardrobePicker = false
                viewModel.onWardrobePicked(wardrobe.id, wardrobe.name)
            },
        )
    }

    if (showCustomTag) {
        CustomTagDialog(
            onDismiss = { showCustomTag = false },
            onConfirm = { tag ->
                showCustomTag = false
                viewModel.addCustomStyleTag(tag)
            },
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDismiss = { showDiscard = false },
            onDiscard = {
                showDiscard = false
                viewModel.discard()
                if (uiState.isEdit) onSavedEdit() else onBack()
            },
        )
    }
}

@Composable
private fun ClothingFormContent(
    state: ClothingFormUiState,
    styleSuggestions: List<String>,
    onCategoryChange: (String?) -> Unit,
    onColorToggle: (String) -> Unit,
    onStyleToggle: (String) -> Unit,
    onSeasonToggle: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onWardrobeRowClick: () -> Unit,
    onAddCustomStyle: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Dimens.Space.s6),
    ) {
        // ── 照片居顶 ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (state.imagePath != null) {
                CmAsyncImage(
                    model = state.imagePath,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.compressing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.Icon.l),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = AppIcons.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(Dimens.Icon.l + Dimens.Space.s4),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {

            // ── 类别：两级 chips（§7.3「类别/颜色/风格用 chips 点选」）──
            CategorySelector(
                selectedKey = state.categoryKey,
                onSelect = onCategoryChange,
            )

            // ── 主颜色：12 色点选（多选） ──────────────────────
            FormSectionLabel(stringResource(R.string.form_section_color))
            ChipGroup(
                options = ClothingColor.entries.map { color ->
                    ChipOption(
                        key = color.key,
                        label = color.displayName,
                        color = com.yuhinhsiung.clothesmate.ui.theme.ClothingColors.dotColor(color),
                    )
                },
                selectedKeys = state.colorKeys.toSet(),
                onToggle = onColorToggle,
            )

            // ── 风格：联想 chips + 自定义 ──────────────────────
            FormSectionLabel(stringResource(R.string.form_section_style))
            FlowRowLikeStyleTags(
                suggestions = styleSuggestions,
                selected = state.styleTags,
                onToggle = onStyleToggle,
                onAddCustom = onAddCustomStyle,
            )

            // ── 适合季节 ──────────────────────────────────────
            FormSectionLabel(stringResource(R.string.form_section_season))
            ChipGroup(
                options = Season.entries.map { season ->
                    ChipOption(key = season.key, label = season.displayName)
                },
                selectedKeys = state.seasonKeys.toSet(),
                onToggle = onSeasonToggle,
            )

            // ── 品牌 / 备注 ───────────────────────────────────
            CmTextField(
                value = state.brand,
                onValueChange = onBrandChange,
                label = stringResource(R.string.form_field_brand),
                placeholder = "可选",
                imeAction = ImeAction.Next,
                modifier = Modifier.padding(top = Dimens.Space.s5),
            )
            CmTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.form_field_note),
                placeholder = "可选",
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = Dimens.Space.s4),
            )

            // ── 所属衣柜行（→ 选择衣柜弹层） ──────────────────
            FormSectionLabel(stringResource(R.string.form_field_wardrobe))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.Radius.s))
                    .background(MaterialTheme.colorScheme.muted)
                    .clickable(onClick = onWardrobeRowClick)
                    .padding(horizontal = Dimens.Space.s3, vertical = Dimens.Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.wardrobeName
                        ?: stringResource(R.string.form_wardrobe_unset),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.wardrobeName != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = AppIcons.More,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(Dimens.Icon.s),
                )
            }
            if (state.wardrobeId == null) {
                Text(
                    text = stringResource(R.string.form_need_wardrobe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Dimens.Space.s1),
                )
            }

            // ── 编号（自动生成可改 + 实时校验，§6.7 ✅/❌ 反馈） ──
            CmTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = stringResource(R.string.form_field_code),
                placeholder = "纯数字，如 102",
                isError = state.codeError != null,
                errorMessage = state.codeError,
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = Dimens.Space.s4),
            )

            // ── 主按钮 ────────────────────────────────────────
            Button(
                onClick = onSave,
                enabled = state.canSave,
                shape = RoundedCornerShape(Dimens.Radius.m),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s5)
                    .height(Dimens.buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary
                        .copy(alpha = Dimens.DisabledAlpha),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = Dimens.DisabledAlpha),
                ),
            ) {
                Text(
                    text = stringResource(
                        if (state.isEdit) R.string.form_save else R.string.form_save_auto,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** 表单分组标签（§6.7「标签常驻上方」） */
@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onMuted,
        modifier = Modifier.padding(top = Dimens.Space.s5, bottom = Dimens.Space.s2),
    )
}

/**
 * 类别两级选择：第一行 = 8 个大类；选中含小类的大类时出现第二行小类。
 * 连衣裙 / 帽子不再细分，选中大类即完成（DB 存大类 key，开发文档 §3.4）。
 */
@Composable
private fun CategorySelector(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    val selectedTop = selectedKey?.let { key ->
        ClothingCategory.fromKey(key)?.topKey
    }
    FormSectionLabel(stringResource(R.string.form_section_category))
    // 第一行：大类（横滑，360dp 小屏不挤压——review U-e 同款处理）
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        items(ClothingCategory.topLevels) { (topKey, topName) ->
            SelectableChip(
                option = ChipOption(key = topKey, label = topName),
                selected = selectedTop == topKey,
                onClick = {
                    if (selectedTop == topKey) {
                        onSelect(null)
                    } else {
                        // 连衣裙 / 帽子：大类即小类，直接定值；其余仅展开小类行
                        val subs = ClothingCategory.entries.filter { it.topKey == topKey }
                        onSelect(if (subs.size == 1) subs.first().key else null)
                    }
                },
            )
        }
    }
    val subs = selectedTop
        ?.let { top -> ClothingCategory.entries.filter { it.topKey == top } }
        .orEmpty()
    if (subs.size > 1) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            modifier = Modifier.padding(top = Dimens.Space.s2),
        ) {
            items(subs) { sub ->
                SelectableChip(
                    option = ChipOption(key = sub.key, label = sub.displayName),
                    selected = selectedKey == sub.key,
                    onClick = {
                        onSelect(if (selectedKey == sub.key) null else sub.key)
                    },
                )
            }
        }
    }
}

/** 风格标签（含末位「＋ 自定义」）——FlowRow 用 foundation 版本（与 ChipGroup 一致） */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLikeStyleTags(
    suggestions: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onAddCustom: () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        (suggestions + selected.filter { it !in suggestions }).forEach { tag ->
            SelectableChip(
                option = ChipOption(key = tag, label = tag),
                selected = tag in selected,
                onClick = { onToggle(tag) },
            )
        }
        SelectableChip(
            option = ChipOption(key = "__custom__", label = stringResource(R.string.form_add_custom)),
            selected = false,
            onClick = onAddCustom,
        )
    }
}

/** 自定义标签输入弹层（M1-21：trim 后进已选，保存时统一写词典） */
@Composable
private fun CustomTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(
                text = stringResource(R.string.form_custom_tag),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            CmTextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.form_section_style),
                placeholder = stringResource(R.string.form_custom_tag_hint),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text) },
            ) {
                Text(
                    text = stringResource(R.string.action_confirm),
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
