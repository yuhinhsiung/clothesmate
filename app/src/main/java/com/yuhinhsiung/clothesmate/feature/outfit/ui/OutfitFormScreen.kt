package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.OutfitCategory
import com.yuhinhsiung.clothesmate.core.model.Season
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
 * 确认穿搭信息页 —— 开发计划 M1-18 / 开发文档 §2.3.3、UI 文档 §7.4、mockup-15/16；编辑复用。
 *
 * 照片区（横滑多图 / 无照片占位）→ 包含衣物区（横滑缩略图 + 衣物选择器）→ 分类多选
 * （＋ 现场新建）→ 风格 / 场景 / 季节 chips → 备注 → 「保存穿搭」。
 * 未选衣物禁用保存并在按钮上方提示（§7.4）。中途返回同 M1-15 规则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitFormRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenCapture: () -> Unit,
    viewModel: OutfitFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val styleSuggestions by viewModel.styleSuggestions.collectAsStateWithLifecycle()
    val sceneSuggestions by viewModel.sceneSuggestions.collectAsStateWithLifecycle()
    val clothingById by viewModel.clothingById.collectAsStateWithLifecycle()

    var showDiscard by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showCustomStyle by remember { mutableStateOf(false) }
    var showCustomScene by remember { mutableStateOf(false) }

    // 相册多选追加（上限 9）
    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(OutfitFormUiState.MAX_OUTFIT_PHOTOS),
    ) { uris -> viewModel.onPhotosPicked(uris) }

    // 拍照追加回执与选择器回传均由 VM 经 NavResultBus 收集（review B-01），屏幕层不再观察

    LaunchedEffect(uiState.notFound) {
        if (uiState.notFound) onBack()
    }

    // M1-15 同规则：有填写内容时返回先确认
    BackHandler(enabled = uiState.userModified && !uiState.saving) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEdit) R.string.outfit_form_title_edit
                            else R.string.outfit_form_title_create,
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
        OutfitFormContent(
            state = uiState,
            categories = categories,
            styleSuggestions = styleSuggestions,
            sceneSuggestions = sceneSuggestions,
            clothingById = clothingById,
            onAddPhotoFromAlbum = { albumLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAddPhotoFromCamera = onOpenCapture,
            onRemovePhoto = viewModel::removePhoto,
            onOpenPicker = {
                // B-19：先发布当前已选种子，选择器进入即预填
                viewModel.seedPicker()
                onOpenPicker()
            },
            onRemoveItem = viewModel::onToggleItem,
            onToggleCategory = viewModel::onToggleCategory,
            onNewCategory = { showNewCategory = true },
            onToggleStyle = viewModel::onToggleStyle,
            onToggleScene = viewModel::onToggleScene,
            onToggleSeason = viewModel::onToggleSeason,
            onAddCustomStyle = { showCustomStyle = true },
            onAddCustomScene = { showCustomScene = true },
            onNoteChange = viewModel::onNoteChange,
            onDismissError = viewModel::dismissError,
            onSave = { viewModel.save(onSaved) },
            modifier = Modifier
                .padding(innerPadding)
                .imePadding(),
        )
    }

    if (showNewCategory) {
        TextInputDialog(
            title = stringResource(R.string.outfit_new_category),
            fieldLabel = stringResource(R.string.outfit_category_name_label),
            onDismiss = { showNewCategory = false },
            onConfirm = { name ->
                showNewCategory = false
                viewModel.createAndSelectCategory(name)
            },
        )
    }
    if (showCustomStyle) {
        TextInputDialog(
            title = stringResource(R.string.form_custom_tag),
            fieldLabel = stringResource(R.string.form_section_style),
            onDismiss = { showCustomStyle = false },
            onConfirm = { tag ->
                showCustomStyle = false
                viewModel.addCustomStyleTag(tag)
            },
        )
    }
    if (showCustomScene) {
        TextInputDialog(
            title = stringResource(R.string.form_custom_tag),
            fieldLabel = stringResource(R.string.outfit_section_scene),
            onDismiss = { showCustomScene = false },
            onConfirm = { tag ->
                showCustomScene = false
                viewModel.addCustomSceneTag(tag)
            },
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDismiss = { showDiscard = false },
            onDiscard = {
                showDiscard = false
                viewModel.discard()
                onBack()
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OutfitFormContent(
    state: OutfitFormUiState,
    categories: List<OutfitCategory>,
    styleSuggestions: List<String>,
    sceneSuggestions: List<String>,
    clothingById: Map<Long, ClothingItem>,
    onAddPhotoFromAlbum: () -> Unit,
    onAddPhotoFromCamera: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    onRemoveItem: (Long) -> Unit,
    onToggleCategory: (Long) -> Unit,
    onNewCategory: () -> Unit,
    onToggleStyle: (String) -> Unit,
    onToggleScene: (String) -> Unit,
    onToggleSeason: (String) -> Unit,
    onAddCustomStyle: () -> Unit,
    onAddCustomScene: () -> Unit,
    onNoteChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.pagePadding)
            .padding(bottom = Dimens.Space.s6),
    ) {
        // ── 照片区：多图横滑（§7.4）────────────────────────────
        OutfitPhotoSection(
            displayPaths = state.existingPaths + state.stagedDisplayPaths,
            canAdd = state.canAddPhoto,
            adding = state.addingPhoto,
            onAddFromAlbum = onAddPhotoFromAlbum,
            onAddFromCamera = onAddPhotoFromCamera,
            onRemove = onRemovePhoto,
        )

        // ── 包含衣物区 ────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Dimens.Space.s5),
        ) {
            Text(
                text = stringResource(R.string.outfit_section_items),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.outfit_items_count, state.selectedItemIds.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.padding(start = Dimens.Space.s2),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            modifier = Modifier.padding(top = Dimens.Space.s2),
        ) {
            itemsIndexed(state.selectedItemIds, key = { _, id -> id }) { _, itemId ->
                val item = clothingById[itemId]
                SelectedItemThumb(
                    item = item,
                    onRemove = { onRemoveItem(itemId) },
                )
            }
            item(key = "add_item") {
                AddTile(
                    label = stringResource(R.string.outfit_add_item),
                    onClick = onOpenPicker,
                )
            }
        }

        // ── 分类多选（＋ 现场新建，O-01）───────────────────────
        FormSectionLabel(stringResource(R.string.outfit_section_categories))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        ) {
            categories.forEach { category ->
                SelectableChip(
                    option = ChipOption(key = category.id.toString(), label = category.name),
                    selected = category.id in state.selectedCategoryIds,
                    onClick = { onToggleCategory(category.id) },
                )
            }
            SelectableChip(
                option = ChipOption(
                    key = "__new_category__",
                    label = stringResource(R.string.outfit_new_category),
                ),
                selected = false,
                onClick = onNewCategory,
            )
        }

        // ── 风格 / 场景 / 季节（多选 + 自定义，§7.4）────────────
        FormSectionLabel(stringResource(R.string.form_section_style))
        TagFlowRow(
            suggestions = styleSuggestions,
            selected = state.styleTags,
            onToggle = onToggleStyle,
            onAddCustom = onAddCustomStyle,
        )

        FormSectionLabel(stringResource(R.string.outfit_section_scene))
        TagFlowRow(
            suggestions = sceneSuggestions,
            selected = state.sceneTags,
            onToggle = onToggleScene,
            onAddCustom = onAddCustomScene,
        )

        FormSectionLabel(stringResource(R.string.form_section_season))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        ) {
            Season.entries.forEach { season ->
                SelectableChip(
                    option = ChipOption(key = season.key, label = season.displayName),
                    selected = season.key in state.seasonTags,
                    onClick = { onToggleSeason(season.key) },
                )
            }
        }

        // ── 备注 ─────────────────────────────────────────────
        CmTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = stringResource(R.string.form_field_note),
            placeholder = "可选",
            imeAction = ImeAction.Done,
            modifier = Modifier.padding(top = Dimens.Space.s5),
        )

        // ── 保存 ─────────────────────────────────────────────
        if (state.selectedItemIds.isEmpty()) {
            // §7.4：未选衣物时按钮上方提示
            Text(
                text = stringResource(R.string.outfit_need_items),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s5),
            )
        }
        // ── 保存失败反馈（review B-06：此前 uiState.error 无任何读取点）──
        state.error?.let { error ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s3)
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
        Button(
            onClick = onSave,
            enabled = state.canSave,
            shape = RoundedCornerShape(Dimens.Radius.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.Space.s2)
                .height(Dimens.buttonHeight),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary
                    .copy(alpha = Dimens.DisabledAlpha),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary
                    .copy(alpha = Dimens.DisabledAlpha),
            ),
        ) {
            Text(
                text = stringResource(R.string.outfit_save),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 照片区：多图横滑 + 末位「+ 添加」；无照片 = 虚线占位卡（§7.4）。 */
@Composable
private fun OutfitPhotoSection(
    displayPaths: List<String>,
    canAdd: Boolean,
    adding: Boolean,
    onAddFromAlbum: () -> Unit,
    onAddFromCamera: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    if (displayPaths.isEmpty()) {
        // 无照片：muted 虚线占位卡 + 补图按钮（§7.4「补一张上身照」）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(Dimens.Radius.l))
                    .background(MaterialTheme.colorScheme.muted),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = AppIcons.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.size(Dimens.Icon.l),
                    )
                    Text(
                        text = stringResource(R.string.outfit_photo_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(top = Dimens.Space.s2),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                modifier = Modifier.padding(top = Dimens.Space.s3),
            ) {
                TextButton(onClick = onAddFromCamera) {
                    Text(stringResource(R.string.outfit_photo_take), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onAddFromAlbum) {
                    Text(stringResource(R.string.outfit_photo_album), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        modifier = Modifier.padding(top = Dimens.Space.s4),
    ) {
        itemsIndexed(displayPaths) { index, path ->
            Box(
                modifier = Modifier
                    .width(Dimens.formPhotoWidth)
                    .aspectRatio(4f / 5f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(Dimens.Radius.m))
                        .background(MaterialTheme.colorScheme.muted),
                ) {
                    CmAsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 首图「封面」chip（§7.4）
                    if (index == 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Dimens.Space.s1)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .padding(horizontal = Dimens.Space.s2, vertical = Dimens.Space.s1 / 2),
                        ) {
                            Text(
                                text = stringResource(R.string.outfit_cover_chip),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                // 右上移除 ×（48dp 热区，视觉 24dp）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(Dimens.minTouchTarget)
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.closeButtonSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.outfit_photo_remove),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(Dimens.Icon.s),
                        )
                    }
                }
            }
        }
        if (canAdd) {
            item(key = "add_photo") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = Dimens.Space.s2),
                ) {
                    AddTile(
                        label = stringResource(R.string.outfit_add_photo),
                        onClick = onAddFromAlbum,
                        width = Dimens.formPhotoWidth,
                        aspect = 4f / 5f,
                    )
                    TextButton(onClick = onAddFromCamera) {
                        Text(
                            text = stringResource(R.string.outfit_photo_take),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** 已选衣物缩略图（抠图 + 编号 chip，点击移除，§7.4）。 */
@Composable
private fun SelectedItemThumb(
    item: ClothingItem?,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(Dimens.formThumbWidth)
            .height(Dimens.formThumbHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Dimens.Radius.m))
                .background(MaterialTheme.colorScheme.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (item?.displayImagePath != null) {
                CmAsyncImage(
                    model = item.displayImagePath,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = AppIcons.Wardrobe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(Dimens.Icon.l),
                )
            }
            if (item != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Dimens.Space.s1)
                        .clip(RoundedCornerShape(Dimens.Radius.s))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = Dimens.Space.s1),
                ) {
                    Text(
                        text = item.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(Dimens.minTouchTarget)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.closeButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.outfit_remove_item),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.Icon.s),
                )
            }
        }
    }
}

/** 虚线「+ 添加」格（照片末位 / 衣物末位共用，§7.4）。 */
@Composable
private fun AddTile(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = Dimens.formThumbWidth,
    aspect: Float = 1.25f,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(width)
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(Dimens.Radius.m))
            .background(MaterialTheme.colorScheme.muted)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = AppIcons.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier.size(Dimens.Icon.m),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimens.Space.s1),
        )
    }
}

/** 标签 FlowRow（联想 + 已选合并展示 + 末位自定义） */
@Composable
private fun TagFlowRow(
    suggestions: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onAddCustom: () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
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

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onMuted,
        modifier = Modifier.padding(top = Dimens.Space.s5, bottom = Dimens.Space.s2),
    )
}

/** 单行文本输入弹层（新建分类 / 自定义标签共用） */
@Composable
private fun TextInputDialog(
    title: String,
    fieldLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            CmTextField(
                value = text,
                onValueChange = { text = it },
                label = fieldLabel,
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
