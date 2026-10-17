package com.yuhinhsiung.clothesmate

import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.feature.addclothing.ui.CaptureScreen
import com.yuhinhsiung.clothesmate.feature.outfit.ui.ClothingPickerRoute
import com.yuhinhsiung.clothesmate.feature.outfit.ui.OutfitDetailRoute
import com.yuhinhsiung.clothesmate.feature.outfit.ui.OutfitFormRoute
import com.yuhinhsiung.clothesmate.feature.outfit.ui.OutfitHomeRoute
import com.yuhinhsiung.clothesmate.feature.outfit.ui.OutfitWaterfallRoute
import com.yuhinhsiung.clothesmate.feature.overview.ui.OverviewRoute
import com.yuhinhsiung.clothesmate.feature.today.ui.TodayRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.ClothingDetailRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.DeleteResult
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.SearchRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDeleteDialog
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDetailRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeEditSheet
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobePickerSheet
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form.ClothingFormRoute
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.component.LocalNavResultBus
import com.yuhinhsiung.clothesmate.ui.navigation.Capture
import com.yuhinhsiung.clothesmate.ui.navigation.CaptureTarget
import com.yuhinhsiung.clothesmate.ui.navigation.ClothingDetail
import com.yuhinhsiung.clothesmate.ui.navigation.ClothingForm
import com.yuhinhsiung.clothesmate.ui.navigation.ClothingPicker
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitDetailArgs
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitForm
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitTab
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitWaterfall
import com.yuhinhsiung.clothesmate.ui.navigation.OverviewTab
import com.yuhinhsiung.clothesmate.ui.navigation.Search
import com.yuhinhsiung.clothesmate.ui.navigation.TodayTab
import com.yuhinhsiung.clothesmate.ui.navigation.WardrobeDetail
import com.yuhinhsiung.clothesmate.ui.navigation.WardrobeTab
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * 底部导航 4 Tab —— 开发文档 §6.1、UI 设计文档 §6.1。
 *
 * 图标经 `AppIcons` 集中引用（UI 文档 §5.1 清单，字形核对见
 * `design-system/clothesmate/pages/icon-mapping-override.md`）。
 * 选中/未选中共用同一字形（Rounded），仅靠颜色区分（review C-14 双字形项维持记录）。
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    /** 标签文案走资源（review E-a） */
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Today(TodayTab, TodayTab::class, R.string.tab_today, AppIcons.Today),
    Wardrobe(WardrobeTab, WardrobeTab::class, R.string.tab_wardrobe, AppIcons.Wardrobe),
    Outfit(OutfitTab, OutfitTab::class, R.string.tab_outfit, AppIcons.Outfit),
    Overview(OverviewTab, OverviewTab::class, R.string.tab_overview, AppIcons.Overview),
}

/** 是否有 FAB 悬浮在当前页（U-h：Snackbar 需上移避开 FAB）——目前仅衣柜列表 / 衣柜详情。 */
private fun hasFloatingFab(entry: NavBackStackEntry?): Boolean =
    entry?.destination?.hasRouteAny(WardrobeTab::class, WardrobeDetail::class) == true

/** @Suppress 仅为收敛 `KClass<*>` 到 `KClass<Any>` 的静态类型；运行时传入的都是真实路由类。 */
@Suppress("UNCHECKED_CAST")
private fun androidx.navigation.NavDestination.hasRouteAny(
    vararg classes: KClass<*>,
): Boolean = classes.any { hasRoute(it as KClass<Any>) }

/** 一级页之间切换的通用选项：单例栈，不堆叠历史。 */
private fun androidx.navigation.NavOptionsBuilder.topLevelNavOptions(graph: androidx.navigation.NavGraph) {
    popUpTo(graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun ClothesMateNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hasRouteAny(dest.routeClass) == true
    }

    // 衣柜相关弹层状态（M1-06/07/08）。rememberSaveable：旋转/深色切换不丢（review C-06）
    var showAddWardrobe by rememberSaveable { mutableStateOf(false) }
    var deleteTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** 非空 = 弹层处于「编辑衣柜」模式，值即被编辑的衣柜 id（review C-03） */
    var editWardrobeId by rememberSaveable { mutableStateOf<Long?>(null) }

    // M1-14：FAB 半屏动作面板（§6.4）与衣柜预选。快速连拍 M4 再加入
    var showClothingPanel by rememberSaveable { mutableStateOf(false) }
    var clothingPreselectWardrobeId by rememberSaveable { mutableStateOf<Long?>(null) }

    // M1-18：创建穿搭入口面板（拍照 / 相册多选 / 跳过）
    var showOutfitPanel by rememberSaveable { mutableStateOf(false) }

    // 导航结果总线（review B-01/B-04：SavedStateHandle 回传失效 → 显式总线）
    val resultBus = LocalNavResultBus.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Snackbar 文案在 Composable 作用域用 stringResource 求值，再交给协程——
    // lint（LocalContextGetResourceValueCall）：在 lambda 里经 context.getString
    // 读资源不感知配置变更（切语言/字号后拿到旧值）
    val addedTpl = stringResource(R.string.wardrobe_added)
    val settingsComingSoon = stringResource(R.string.settings_coming_soon)
    fun showSnack(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val transferredTpl = stringResource(R.string.wardrobe_delete_snackbar_transferred)
    val deletedTpl = stringResource(R.string.wardrobe_delete_snackbar_deleted)
    fun deleteSnackText(count: Int, target: String?): String =
        if (target != null) transferredTpl.format(count, target) else deletedTpl.format(count)

    /** 当前详情页正在看的衣柜 id（C-12：详情页「更多」菜单需要它定位目标） */
    fun currentWardrobeId(): Long =
        backStackEntry?.toRouteOrNull<WardrobeDetail>()?.wardrobeId ?: 0L

    // M1-14：相册导入（单张）。选完直接进录入确认页，带上衣柜预选
    val albumPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            navController.navigate(
                ClothingForm(imageUri = it.toString(), wardrobeId = clothingPreselectWardrobeId),
            )
        }
    }

    // M1-18：穿搭照片多选（上限 9）
    val outfitPhotosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Dimens.MaxPhotos),
    ) { uris ->
        if (uris.isNotEmpty()) {
            navController.navigate(OutfitForm(photoUris = uris.joinToString("\n") { it.toString() }))
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // NavigationBar 无内建顶部分割线，按 UI 文档 §6.1 自补一条 outline 细线
                    HorizontalDivider(
                        thickness = Dimens.dividerThickness,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // 关掉 M3 的 tonal elevation，避免 surface 被泛灰色罩住偏离 token
                        tonalElevation = Dimens.Elevation.none,
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentDestination?.hasRouteAny(destination.routeClass) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        // 必须走 reified 实例重载 navigate(TodayTab)：KClass 重载
                                        // 运行时对 @Serializable data object 解析不到 serializer，
                                        // 点 Tab 直接崩（真机冒烟抓出；见Routes.kt E-b 注）
                                        when (destination) {
                                            TopLevelDestination.Today -> navController.navigate(TodayTab) {
                                                topLevelNavOptions(navController.graph)
                                            }

                                            TopLevelDestination.Wardrobe -> navController.navigate(WardrobeTab) {
                                                topLevelNavOptions(navController.graph)
                                            }

                                            TopLevelDestination.Outfit -> navController.navigate(OutfitTab) {
                                                topLevelNavOptions(navController.graph)
                                            }

                                            TopLevelDestination.Overview -> navController.navigate(OverviewTab) {
                                                topLevelNavOptions(navController.graph)
                                            }
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.labelRes),
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                                // 显式指定品牌色，不用 NavigationBarItemDefaults.colors() 空参默认
                                // （其 secondaryContainer 回落 M3 基线紫灰，与玫瑰色板无关）
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onMuted,
                                    unselectedTextColor = MaterialTheme.colorScheme.onMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            // U-h：有 FAB 的页面 Snackbar 上移，避免压在「+ 录衣服」按钮上（§6.8「避开 FAB」）
            val bottomGap = if (hasFloatingFab(backStackEntry)) {
                Dimens.fabSize + Dimens.Space.s4
            } else {
                0.dp
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomGap),
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayTab,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayTab> { TodayRoute() }
            composable<WardrobeTab> {
                WardrobeRoute(
                    onWardrobeClick = { id -> navController.navigate(WardrobeDetail(id)) },
                    onShowAddSheet = { showAddWardrobe = true },
                    onEditWardrobe = { id ->
                        editWardrobeId = id
                        showAddWardrobe = true
                    },
                    onShowDelete = { id -> deleteTargetId = id },
                    // M1-14：FAB 打开半屏动作面板（§6.4）
                    onAddClothing = {
                        clothingPreselectWardrobeId = null
                        showClothingPanel = true
                    },
                    // M1-16：常驻搜索框 → 搜索结果二级页
                    onOpenSearch = { navController.navigate(Search) },
                )
            }
            composable<OutfitTab> {
                OutfitHomeRoute(
                    onCreateOutfit = { showOutfitPanel = true },
                    onOpenAllOutfits = { navController.navigate(OutfitWaterfall(categoryId = null)) },
                    onOpenCategory = { id -> navController.navigate(OutfitWaterfall(categoryId = id)) },
                )
            }
            composable<OverviewTab> {
                OverviewRoute(
                    // 设置页唯一入口（§6.2）；页面随 M2-05 交付，先给明确反馈
                    onOpenSettings = { showSnack(settingsComingSoon) },
                    onAddClothing = {
                        navController.navigate(Capture(target = CaptureTarget.CLOTHING_NEW))
                    },
                )
            }
            composable<OutfitWaterfall> {
                OutfitWaterfallRoute(
                    onBack = { navController.popBackStack() },
                    onOutfitClick = { outfitId -> navController.navigate(OutfitDetailArgs(outfitId)) },
                    onCreateOutfit = { showOutfitPanel = true },
                )
            }
            composable<OutfitDetailArgs> {
                OutfitDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { outfitId -> navController.navigate(OutfitForm(outfitId = outfitId)) },
                    onItemDetailClick = { itemId -> navController.navigate(ClothingDetail(itemId)) },
                )
            }
            composable<OutfitForm> {
                OutfitFormRoute(
                    onBack = { navController.popBackStack() },
                    // 创建 → 回穿搭分类首页；编辑 → 回详情页（经结果总线触发「已保存」Snackbar，
                    // review B-04：SavedStateHandle 回传在本工程失效，改走 NavResultBus）
                    onSaved = {
                        val isEdit = navController.currentBackStackEntry
                            ?.toRouteOrNull<OutfitForm>()?.outfitId != null
                        if (isEdit) {
                            resultBus.publishOutfitSaved()
                        }
                        navController.popBackStack()
                    },
                    onOpenPicker = { navController.navigate(ClothingPicker) },
                    onOpenCapture = {
                        navController.navigate(Capture(target = CaptureTarget.OUTFIT_APPEND))
                    },
                )
            }
            composable<ClothingPicker> {
                // 结果已由选择器 VM 经 NavResultBus 发布（B-01），这里只负责收页
                ClothingPickerRoute(onDone = { navController.popBackStack() })
            }
            composable<WardrobeDetail> {
                // review E-c：衣柜 id 由 ViewModel 从 SavedStateHandle 自取，不再经形参
                WardrobeDetailRoute(
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId -> navController.navigate(ClothingDetail(itemId)) },
                    // M1-14：详情页 FAB 带当前衣柜作为预选
                    onAddClothing = { wardrobeId ->
                        clothingPreselectWardrobeId = wardrobeId
                        showClothingPanel = true
                    },
                    // review C-12：详情页「更多」菜单的两个去向
                    onEditWardrobe = {
                        editWardrobeId = currentWardrobeId()
                        showAddWardrobe = true
                    },
                    onDeleteWardrobe = {
                        deleteTargetId = currentWardrobeId()
                    },
                )
            }
            composable<ClothingDetail> {
                ClothingDetailRoute(
                    onBack = { navController.popBackStack() },
                    onWardrobeClick = { wardrobeId ->
                        navController.navigate(WardrobeDetail(wardrobeId)) {
                            launchSingleTop = true
                        }
                    },
                    // M1-12：衣物编辑页（复用确认页表单）
                    onEdit = { itemId -> navController.navigate(ClothingForm(itemId = itemId)) },
                )
            }
            composable<Capture> { entry ->
                val route = entry.toRoute<Capture>()
                CaptureScreen(
                    onBack = { navController.popBackStack() },
                    onCaptured = { imageUri ->
                        when (route.target) {
                            CaptureTarget.CLOTHING_NEW -> navController.navigate(
                                ClothingForm(imageUri = imageUri, wardrobeId = route.wardrobeId),
                            ) {
                                // 拍照页使命完成，弹掉自己：确认页返回 → 放弃确认 → 直接回 Tab
                                popUpTo<Capture> { inclusive = true }
                            }

                            CaptureTarget.OUTFIT_NEW -> navController.navigate(
                                com.yuhinhsiung.clothesmate.ui.navigation.OutfitForm(photoUris = imageUri),
                            ) {
                                popUpTo<Capture> { inclusive = true }
                            }

                            // 穿搭确认页内追加：经结果总线回传后收掉自己（M1-18 / B-01）
                            CaptureTarget.OUTFIT_APPEND -> {
                                resultBus.publishAppendCaptured(imageUri)
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }
            composable<Search> {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onClothingClick = { itemId -> navController.navigate(ClothingDetail(itemId)) },
                    onOutfitClick = { outfitId -> navController.navigate(OutfitDetailArgs(outfitId)) },
                )
            }
            composable<ClothingForm> {
                ClothingFormRoute(
                    onBack = { navController.popBackStack() },
                    // M1-14 验收：保存后分配编号并回到衣柜详情
                    onSavedCreate = { wardrobeId ->
                        navController.navigate(WardrobeTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                        }
                        navController.navigate(WardrobeDetail(wardrobeId)) { launchSingleTop = true }
                    },
                    // M1-12：编辑保存 → 回详情页并触发「已保存」Snackbar（B-04/B-05：
                    // 回执走 NavResultBus；放弃路径不复用本回调，见 ClothingFormScreen）
                    onSavedEdit = {
                        resultBus.publishClothingSaved()
                        navController.popBackStack()
                    },
                )
            }
        }

        // ── 衣柜相关弹层（M1-06/07/08） ──────────────────────────
        if (showAddWardrobe) {
            WardrobeEditSheet(
                // 非空 = 编辑既有衣柜（预填）；null = 新建（review C-03）
                wardrobeId = editWardrobeId,
                onDismiss = {
                    showAddWardrobe = false
                    editWardrobeId = null
                },
                onSaved = { _, name ->
                    showAddWardrobe = false
                    editWardrobeId = null
                    // UI 文档 §7.2：保存成功 Snackbar「已添加 · {名称}」（review U-r）
                    showSnack(addedTpl.format(name))
                },
            )
        }

        deleteTargetId?.let { id ->
            WardrobeDeleteDialog(
                wardrobeId = id,
                onDismiss = { deleteTargetId = null },
                onDeleted = { result: DeleteResult ->
                    deleteTargetId = null
                    // review C-04：必须走占位符 string resource
                    showSnack(deleteSnackText(result.count, result.transferredTo))
                    // C-12：删除若是从详情页发起的，衣柜已不存在，退回列表页
                    if (currentDestination?.hasRouteAny(WardrobeDetail::class) == true) {
                        navController.popBackStack()
                    }
                },
            )
        }

        // ── FAB 半屏动作面板（M1-14，§6.4：完整录入 / 从相册导入；快速连拍 M4 加入） ──
        if (showClothingPanel) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showClothingPanel = false },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                ),
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(
                    topStart = Dimens.Radius.xl,
                    topEnd = Dimens.Radius.xl,
                ),
            ) {
                FabActionPanel(
                    title = stringResource(R.string.fab_panel_title),
                    actions = listOf(
                        FabAction(
                            icon = AppIcons.PhotoCamera,
                            title = stringResource(R.string.fab_panel_full),
                            description = stringResource(R.string.fab_panel_full_desc),
                            onClick = {
                                showClothingPanel = false
                                navController.navigate(
                                    Capture(
                                        target = CaptureTarget.CLOTHING_NEW,
                                        wardrobeId = clothingPreselectWardrobeId,
                                    ),
                                )
                            },
                        ),
                        FabAction(
                            icon = AppIcons.Image,
                            title = stringResource(R.string.fab_panel_album),
                            description = stringResource(R.string.fab_panel_album_desc),
                            onClick = {
                                showClothingPanel = false
                                albumPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        ),
                    ),
                )
            }
        }

        // ── 创建穿搭入口面板（M1-18，§7.4：拍照 / 相册多选 / 跳过） ──
        if (showOutfitPanel) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showOutfitPanel = false },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                ),
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(
                    topStart = Dimens.Radius.xl,
                    topEnd = Dimens.Radius.xl,
                ),
            ) {
                FabActionPanel(
                    title = stringResource(R.string.outfit_create),
                    actions = listOf(
                        FabAction(
                            icon = AppIcons.PhotoCamera,
                            title = stringResource(R.string.outfit_photo_take),
                            description = stringResource(R.string.outfit_panel_camera_desc),
                            onClick = {
                                showOutfitPanel = false
                                navController.navigate(Capture(target = CaptureTarget.OUTFIT_NEW))
                            },
                        ),
                        FabAction(
                            icon = AppIcons.Image,
                            title = stringResource(R.string.outfit_photo_album),
                            description = stringResource(R.string.outfit_panel_album_desc),
                            onClick = {
                                showOutfitPanel = false
                                outfitPhotosPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        ),
                        FabAction(
                            icon = AppIcons.Edit,
                            title = stringResource(R.string.outfit_panel_skip),
                            description = stringResource(R.string.outfit_panel_skip_desc),
                            onClick = {
                                showOutfitPanel = false
                                navController.navigate(OutfitForm())
                            },
                        ),
                    ),
                )
            }
        }
    }
}

/** FAB 动作面板的单个动作 */
private data class FabAction(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * 半屏动作面板内容（§6.4 / mockup-13 的入口面板）。行式大触控区，图标 + 标题 + 副文案。
 */
@Composable
private fun FabActionPanel(
    title: String,
    actions: List<FabAction>,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = Dimens.pagePadding)
            .padding(bottom = Dimens.Space.s6),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = Dimens.Space.s3),
        )
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = action.onClick)
                    .padding(vertical = Dimens.Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.Icon.l),
                )
                Column(modifier = Modifier.padding(start = Dimens.Space.s3)) {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                }
            }
        }
    }
}

/** 类型安全路由从当前栈条目读参（读不到返回 null，不抛）。 */
private inline fun <reified T : Any> NavBackStackEntry.toRouteOrNull(): T? =
    runCatching { toRoute<T>() }.getOrNull()
