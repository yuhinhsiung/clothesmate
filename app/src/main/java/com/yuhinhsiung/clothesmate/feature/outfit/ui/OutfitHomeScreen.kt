package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.OutfitCategory
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 穿搭分类首页 —— 开发计划 M1-17 / 开发文档 §2.3.2 O-01/O-02/O-05、UI 文档 §7.4、mockup-07。
 *
 * 顶部固定「全部穿搭」大卡入口（O-04 的门面）+ 分类卡纵列（封面 4:3 + 名称 + 套数）；
 * 右上「+」创建穿搭；分类卡「更多」= 重命名 / 删除（删除弹窗说明连带关系，O-05）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitHomeRoute(
    onCreateOutfit: () -> Unit,
    onOpenAllOutfits: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    viewModel: OutfitHomeViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()

    var renameTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_outfit),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    IconButton(onClick = onCreateOutfit) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = stringResource(R.string.outfit_create),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s4),
        ) {
            // 顶部固定「全部穿搭」大卡（O-02）
            item(key = "all") {
                AllOutfitsCard(
                    count = totalCount,
                    onClick = onOpenAllOutfits,
                )
            }
            items(categories, key = { it.id }) { category ->
                CategoryCard(
                    category = category,
                    onClick = { onOpenCategory(category.id) },
                    onRename = { renameTargetId = category.id },
                    onDelete = { deleteTargetId = category.id },
                )
            }
        }
    }

    renameTargetId?.let { id ->
        val current = categories.firstOrNull { it.id == id }
        if (current != null) {
            RenameCategoryDialog(
                initialName = current.name,
                onDismiss = { renameTargetId = null },
                onConfirm = { name ->
                    renameTargetId = null
                    viewModel.renameCategory(id, name)
                },
            )
        }
    }

    deleteTargetId?.let { id ->
        val current = categories.firstOrNull { it.id == id }
        if (current != null) {
            DeleteCategoryDialog(
                category = current,
                onDismiss = { deleteTargetId = null },
                onConfirm = {
                    deleteTargetId = null
                    viewModel.deleteCategory(id)
                },
            )
        }
    }
}

/** 「全部穿搭」固定大卡入口（O-02/O-04）。 */
@Composable
private fun AllOutfitsCard(
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(Dimens.Radius.l))
            .background(MaterialTheme.colorScheme.muted)
            .clickable(onClick = onClick),
    ) {
        // 封面暂无独立素材：以 muted 底 + 图标占位，套数与标题用深色文字保证可读
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(Dimens.Space.s5),
        ) {
            Icon(
                imageVector = AppIcons.Outfit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.size(Dimens.Icon.l),
            )
            Text(
                text = stringResource(R.string.outfit_all_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Dimens.Space.s2),
            )
            Text(
                text = stringResource(R.string.outfit_count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
    }
}

/** 分类卡：封面 4:3 + 名称 + 套数 + 「更多」（重命名 / 删除）。 */
@Composable
private fun CategoryCard(
    category: OutfitCategory,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    androidx.compose.material3.Card(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(Dimens.Radius.m))
                        .background(MaterialTheme.colorScheme.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    if (category.coverPath != null) {
                        CmAsyncImage(
                            model = category.coverPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Tag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onMuted,
                            modifier = Modifier.size(Dimens.Icon.l),
                        )
                    }
                }
                Column(modifier = Modifier.padding(Dimens.Space.s3)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.outfit_count, category.outfitCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(top = Dimens.Space.s1),
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = AppIcons.More,
                        contentDescription = stringResource(R.string.outfit_category_more),
                        tint = MaterialTheme.colorScheme.onMuted,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.outfit_category_rename)) },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.outfit_category_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** 重命名分类弹层。 */
@Composable
private fun RenameCategoryDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(
                text = stringResource(R.string.outfit_rename_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            CmTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.outfit_category_name_label),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
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

/** 删除分类弹层（O-05）：只解除关联，穿搭保留、无关联者归「未分类」。 */
@Composable
private fun DeleteCategoryDialog(
    category: OutfitCategory,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(
                text = stringResource(R.string.outfit_delete_category_title, category.name),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.outfit_delete_category_body,
                    category.outfitCount,
                ),
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
