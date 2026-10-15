package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.ClothingColors
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 衣物详情页 —— 开发计划 M1-11 / 开发文档 §2.1.2 W-05、UI 文档 §7.2、mockup-05。
 *
 * 顶部大图 →「No.102」大标题 + 名称 → 属性卡 → 底部操作（删除 / 收起 / 编辑）。
 * 删除确认弹层按 W-13（M1-13）实现，含被引用套数提示与撤销。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    onWardrobeClick: (Long) -> Unit = {},
    viewModel: ClothingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    val item = uiState.item
    if (uiState.notFound) {
        // 衣物已不存在（删除后 Flow 置空）→ 直接退回上一页
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (item != null) {
                // 顶部大图：抠图置于 muted 底，M1 无抠图走原图
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .background(MaterialTheme.colorScheme.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = item.displayImagePath
                    if (image != null) {
                        AsyncImage(
                            model = image,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
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
                AttributeCard(
                    label = stringResource(R.string.clothing_attr_wear),
                    value = uiState.wearLabel,
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

                // 底部操作：删除 / 收起 / 编辑
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
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
        ClothingDeleteDialog(
            code = item.code,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                viewModel.softDelete()
                onBack()
            },
        )
    }
}

/** 属性行：左标签（onMuted）+ 右值；可点时给视觉提示 */
@Composable
private fun AttributeCard(
    label: String,
    value: String,
    colorDots: List<Pair<String, ClothingColor?>> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .padding(horizontal = Dimens.pagePadding, vertical = Dimens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier.size(width = 72.dp, height = 20.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 颜色行画色点（§6.5 颜色色块）。ClothingColor 只带 key/displayName，
            // 具体色值查 ClothingColors（UI 文档 §2.1 的 12 色 + 花色）
            colorDots.forEach { (_, color) ->
                val dot = color?.let { ClothingColors.dotColor(it) }
                if (dot != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = Dimens.Space.s1)
                            .size(Dimens.Icon.s)
                            .clip(CircleShape)
                            .background(dot),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 删除衣物确认（W-13）—— AlertDialog radius-l。
 *
 * M1 阶段没有穿搭关联（M1-17 之后才有），「被 N 套穿搭引用」提示见 M1-20 之后补齐；
 * 这里把钩子留好：引用数为 0 时不显示那行。
 */
@Composable
private fun ClothingDeleteDialog(
    code: String,
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
            Text(
                text = stringResource(R.string.clothing_delete_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
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
