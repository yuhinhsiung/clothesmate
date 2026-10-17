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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** 表单当前已选的衣柜 id（review B-09：§7.2 默认选中第一位，此前从未接通） */
    currentSelected: Long? = null,
    viewModel: WardrobePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 两处都用 rememberSaveable：旋转/切字号后内嵌的「新建衣柜」不会被莫名关掉（review A-01）
    var showCreate by rememberSaveable { mutableStateOf(false) }
    /** 内嵌新建弹层的会话身份——只在真正打开时递增，配置重建时保持不变 */
    var createSession by rememberSaveable { mutableIntStateOf(0) }

    fun openCreate() {
        createSession++
        showCreate = true
    }

    // review B-31：副作用从 remember 块迁到 LaunchedEffect（规范做法）
    LaunchedEffect(Unit) { viewModel.load(currentSelected) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // review C-13: 弹层顶部只保留自绘小横条，关掉 M3 默认 DragHandle，
        // 否则会出现「双横条」
        dragHandle = null,
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
                        onAction = { openCreate() },
                    ),
                )
            } else {
                WardrobePickerList(
                    wardrobes = uiState.wardrobes,
                    selectedId = uiState.selectedId,
                    // review C-01：点既有衣柜行 = 选中 + 写回 DataStore + 回传 + 关闭。
                    // 原先只调 onSelect（仅改选中态），弹层永远停住、DataStore 不写。
                    onSelect = { id ->
                        viewModel.onSelect(id)
                        viewModel.confirm(onPicked)
                        onDismiss()
                    },
                )
            }

            // 底部「＋ 新建衣柜」次按钮：有衣柜时常驻，无衣柜时上面空态已提供入口
            if (!uiState.isEmpty) {
                Button(
                    onClick = { openCreate() },
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
            // review A-01：会话身份跨配置重建保持，内嵌新建页的草稿不再被旋转清空
            sessionId = createSession,
            onDismiss = { showCreate = false },
            onSaved = { newId, _ ->
                showCreate = false
                // review R-04：**带上刚建的 id 直接确认**。列表 Flow 未必已经重发到这一帧，
                // confirm 会据此回源查库取对象（原先在旧快照里找不到 id 就静默 return：
                // 新衣柜已落库，弹层却停在旧列表、表单也没回传）。
                // 回传后连同选择弹层一起关掉——用户已经在表单里看到结果了。
                viewModel.confirm(onPicked, targetId = newId)
                onDismiss()
            },
        )
    }
}
