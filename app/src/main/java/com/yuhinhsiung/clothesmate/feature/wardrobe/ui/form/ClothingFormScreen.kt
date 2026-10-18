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
import com.yuhinhsiung.clothesmate.ui.component.AiPrivacyDialog
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.component.DiscardChangesDialog
import com.yuhinhsiung.clothesmate.ui.component.ShimmerCard
import com.yuhinhsiung.clothesmate.ui.component.SelectableChip
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.accentText
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
    /** M2-04 降级引导「去配置」→ 设置页 */
    onOpenSettings: () -> Unit = {},
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
                actions = {
                    // M2-04「全部采纳」：AI 结果返回后出现在顶栏（mockup-06）
                    if (uiState.aiSuggestions != null) {
                        TextButton(onClick = viewModel::acceptAll) {
                            Text(
                                text = stringResource(R.string.ai_accept_all),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
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
            onTopCategorySelect = viewModel::onTopCategoryChange,
            onSubCategorySelect = viewModel::onSubCategoryChange,
            onColorToggle = viewModel::onColorToggle,
            onStyleToggle = viewModel::onStyleToggle,
            onSeasonToggle = viewModel::onSeasonToggle,
            onNameChange = viewModel::onNameChange,
            onBrandChange = viewModel::onBrandChange,
            onNoteChange = viewModel::onNoteChange,
            onCodeChange = viewModel::onCodeChange,
            onDismissError = viewModel::dismissError,
            onWardrobeRowClick = { showWardrobePicker = true },
            onAddCustomStyle = { showCustomTag = true },
            onCancelAi = viewModel::cancelRecognition,
            onOpenSettings = onOpenSettings,
            onRetryImage = viewModel::retryImageStash,
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

    // M2-06：首次触发 AI 识别前的一次性隐私告知，同意后才发起请求
    if (uiState.showPrivacyDialog) {
        AiPrivacyDialog(
            endpointHost = uiState.privacyEndpointHost,
            onAccept = viewModel::acceptPrivacy,
            onDecline = viewModel::declinePrivacy,
        )
    }

    // review B-09：选择衣柜弹层带「当前已选」（§7.2 默认选中优先级第一位），
    // 否则上次使用的衣柜会错误高亮、确认后衣物被静默改挂
    if (showWardrobePicker) {
        com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobePickerSheet(
            currentSelected = uiState.wardrobeId,
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
                // review B-05：放弃 = 普通返回，**不得**复用保存回调——
                // 否则 B-04 修好后「放弃」会弹「已保存」假成功反馈
                onBack()
            },
        )
    }
}

@Composable
private fun ClothingFormContent(
    state: ClothingFormUiState,
    styleSuggestions: List<String>,
    onTopCategorySelect: (String) -> Unit,
    onSubCategorySelect: (String) -> Unit,
    onColorToggle: (String) -> Unit,
    onStyleToggle: (String) -> Unit,
    onSeasonToggle: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onWardrobeRowClick: () -> Unit,
    onAddCustomStyle: () -> Unit,
    onCancelAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryImage: () -> Unit,
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
                    // 抠图 PNG 是透明底，Crop 会裁掉主体边缘；原图仍用 Crop 填满
                    contentScale = if (state.imageIsCutout) {
                        androidx.compose.ui.layout.ContentScale.Fit
                    } else {
                        androidx.compose.ui.layout.ContentScale.Crop
                    },
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

            // ── R02：恢复时原图暂存失效，保存已禁用，给重试/返回重选入口 ──
            if (state.imageUnavailable && !state.isEdit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.Space.s3)
                        .clip(RoundedCornerShape(Dimens.Radius.s))
                        .background(MaterialTheme.colorScheme.muted)
                        .padding(start = Dimens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.form_image_missing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetryImage) {
                        Text(
                            text = stringResource(R.string.form_image_retry),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            // ── M2-04 AI 三态区（识别中 / 已预填 / 降级） ─────────
            AiStatusBanner(
                state = state,
                onCancel = onCancelAi,
                onOpenSettings = onOpenSettings,
            )

            // ── 名称（review R-08）：**仅编辑模式** ───────────────
            // M1-12 要求「全字段可改」，name 此前没有入口；创建确认页按 §2.3.3
            // 不提供名称输入（不强迫用户起名），所以这里用 isEdit 门控。
            // 清空即回到「主色 · 类别」兜底名。
            if (state.isEdit) {
                CmTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.form_field_name),
                    placeholder = stringResource(R.string.form_field_name_hint),
                    singleLine = true,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.padding(top = Dimens.Space.s5),
                )
            }

            // ── 类别：两级 chips（§7.3「类别/颜色/风格用 chips 点选」）──
            CategorySelector(
                expandedTopKey = state.expandedTopKey,
                selectedKey = state.categoryKey,
                aiFilled = state.categoryAiFilled,
                onTopSelect = onTopCategorySelect,
                onSubSelect = onSubCategorySelect,
            )

            // ── 主颜色：12 色点选（多选） ──────────────────────
            FormSectionLabel(
                text = stringResource(R.string.form_section_color),
                // 本地取色**不是 AI**：中性文字标识，不用 ✨ 琥珀金（用户 2026-10-18 拍板）
                localBadge = if (state.colorsAutoFilled) {
                    stringResource(R.string.color_local_badge)
                } else {
                    null
                },
            )
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
                // 本地取色的选中态用普通 primaryContainer，不加 AI 金色描边
                accentSelectedKeys = emptySet(),
            )

            // ── 风格：联想 chips + 自定义 ──────────────────────
            FormSectionLabel(
                text = stringResource(R.string.form_section_style),
                aiBadge = if (state.styleAiFilled) stringResource(R.string.ai_badge) else null,
            )
            FlowRowLikeStyleTags(
                suggestions = styleSuggestions,
                selected = state.styleTags,
                accentSelected = state.styleAiFilled,
                onToggle = onStyleToggle,
                onAddCustom = onAddCustomStyle,
            )

            // ── 适合季节 ──────────────────────────────────────
            FormSectionLabel(
                text = stringResource(R.string.form_section_season),
                aiBadge = if (state.seasonAiFilled) stringResource(R.string.ai_badge) else null,
            )
            ChipGroup(
                options = Season.entries.map { season ->
                    ChipOption(key = season.key, label = season.displayName)
                },
                selectedKeys = state.seasonKeys.toSet(),
                onToggle = onSeasonToggle,
                accentSelectedKeys = if (state.seasonAiFilled) state.seasonKeys.toSet() else emptySet(),
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

            // ── 保存失败反馈（review B-06：此前 uiState.error 无任何读取点，
            // 磁盘满 / 暂存图被清 / 编号竞态时界面毫无变化，用户会反复点击）──
            state.error?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.Space.s4)
                        .clip(RoundedCornerShape(Dimens.Radius.s))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                        .clickable(onClick = onDismissError)
                        .padding(horizontal = Dimens.Space.s3, vertical = Dimens.Space.s2),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.error_dismiss),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

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

/** 表单分组标签（§6.7「标签常驻上方」）；可带 ✨ AI 角标或中性「本地提取」文字。 */
@Composable
private fun FormSectionLabel(
    text: String,
    aiBadge: String? = null,
    localBadge: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Dimens.Space.s5, bottom = Dimens.Space.s2),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
        if (aiBadge != null) {
            AiBadge(label = aiBadge, modifier = Modifier.padding(start = Dimens.Space.s1))
        } else if (localBadge != null) {
            Text(
                text = localBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.padding(start = Dimens.Space.s1),
            )
        }
    }
}

/**
 * ✨ AI 角标（UI 文档 §6.6）：琥珀金图标 + 文字。
 *
 * 整个「AI 预填字段区」共用金色，按 UI 文档 §2.1 属于**一处** AI 标识区；
 * 不会与「待完善」等稀有高亮叠加（本页没有后者）。
 */
@Composable
private fun AiBadge(label: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = AppIcons.Ai,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(Dimens.Icon.s),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.accentText,
            modifier = Modifier.padding(start = Dimens.Space.s1),
        )
    }
}

/**
 * 类别两级选择（review B-02 修复版）：
 * 第一行 = 8 个大类，点选 = 展开该大类的小类行并保持大类高亮（单小类大类
 * 「连衣裙/帽子」直接定值；再点同一大类收起小类行）；第二行 = 小类，点选定值。
 * 「已展开大类」是独立状态（UI state 的 expandedTopKey），不再由选中值反推——
 * 旧实现多小类大类点开即把值置空，二级行永不渲染、8 个大类里 6 个选不中。
 */
@Composable
internal fun CategorySelector(
    expandedTopKey: String?,
    selectedKey: String?,
    onTopSelect: (String) -> Unit,
    onSubSelect: (String) -> Unit,
    aiFilled: Boolean = false,
) {
    FormSectionLabel(
        text = stringResource(R.string.form_section_category),
        aiBadge = if (aiFilled) stringResource(R.string.ai_badge) else null,
    )
    // 第一行：大类（横滑，360dp 小屏不挤压——review U-e 同款处理）
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        items(ClothingCategory.topLevels) { (topKey, topName) ->
            SelectableChip(
                option = ChipOption(key = topKey, label = topName),
                selected = expandedTopKey == topKey,
                onClick = { onTopSelect(topKey) },
            )
        }
    }
    val subs = expandedTopKey
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
                    accentBorder = aiFilled && selectedKey == sub.key,
                    onClick = { onSubSelect(sub.key) },
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
    accentSelected: Boolean,
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
                accentBorder = accentSelected && tag in selected,
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


// ── M2-04 / M2-06 组件 ─────────────────────────────────────────

/**
 * 字段区上方的 AI 三态区（UI 文档 §7.3）。
 *
 * 识别中 = shimmer + 「AI 识别中…」+ [取消识别]；
 * 已预填 = 一行提示（字段各自的 ✨ 角标在字段标题上）；
 * 降级 = 未配置/拒绝显示引导入口，失败/取消只给一句「可直接填写」，不弹错误。
 */
@Composable
private fun AiStatusBanner(
    state: ClothingFormUiState,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state.aiPhase) {
        AiPhase.Running -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s3),
            ) {
                ShimmerCard(height = 48.dp, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = Dimens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ai_running),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.ai_cancel),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        AiPhase.Prefilled -> AiHintRow(
            text = stringResource(R.string.ai_prefilled_hint),
            modifier = Modifier.padding(top = Dimens.Space.s3),
        )

        AiPhase.Degraded -> when (state.aiDegrade) {
            AiDegradeReason.NotConfigured -> AiGuideCard(
                title = stringResource(R.string.ai_not_configured_title),
                description = stringResource(R.string.ai_not_configured_desc),
                actionLabel = stringResource(R.string.ai_go_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(top = Dimens.Space.s3),
            )

            AiDegradeReason.Declined -> AiHintRow(
                text = stringResource(R.string.ai_consent_declined_hint),
                actionLabel = stringResource(R.string.ai_go_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(top = Dimens.Space.s3),
            )

            AiDegradeReason.Failed,
            AiDegradeReason.Cancelled,
            null,
            -> AiHintRow(
                text = stringResource(R.string.ai_failed_hint),
                modifier = Modifier.padding(top = Dimens.Space.s3),
            )
        }

        else -> Unit
    }
}

@Composable
private fun AiHintRow(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius.s))
            .background(MaterialTheme.colorScheme.muted)
            .padding(horizontal = Dimens.Space.s3, vertical = Dimens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Ai,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(Dimens.Icon.s),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.Space.s2),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun AiGuideCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius.m))
            .background(MaterialTheme.colorScheme.muted)
            .padding(Dimens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Ai,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(Dimens.Icon.s),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.Space.s2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
        TextButton(onClick = onAction) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
