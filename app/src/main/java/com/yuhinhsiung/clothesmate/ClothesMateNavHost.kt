package com.yuhinhsiung.clothesmate

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.yuhinhsiung.clothesmate.feature.overview.ui.OverviewRoute
import com.yuhinhsiung.clothesmate.feature.outfit.ui.OutfitHomeRoute
import com.yuhinhsiung.clothesmate.feature.today.ui.TodayRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.DeleteResult
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.ClothingDetailRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDeleteDialog
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDetailRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeEditSheet
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobePickerSheet
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeRoute
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.navigation.ClothingDetail
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitTab
import com.yuhinhsiung.clothesmate.ui.navigation.OverviewTab
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Snackbar 文案在 Composable 作用域用 stringResource 求值，再交给协程——
    // lint（LocalContextGetResourceValueCall）：在 lambda 里经 context.getString
    // 读资源不感知配置变更（切语言/字号后拿到旧值）
    val comingSoonText = stringResource(R.string.coming_soon_camera)
    val addedTpl = stringResource(R.string.wardrobe_added)
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
                                        @Suppress("UNCHECKED_CAST")
                                        navController.navigate(destination.routeClass as KClass<Any>) {
                                            // 一级页之间切换：单例栈，不堆叠历史
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
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
                    // review C-02/C-11：FAB 语义是「+ 录衣服」（§6.4）。M1-14 实装前给反馈
                    onAddClothing = { showSnack(comingSoonText) },
                )
            }
            composable<OutfitTab> { OutfitHomeRoute() }
            composable<OverviewTab> { OverviewRoute() }
            composable<WardrobeDetail> {
                // review E-c：衣柜 id 由 ViewModel 从 SavedStateHandle 自取，不再经形参
                WardrobeDetailRoute(
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId -> navController.navigate(ClothingDetail(itemId)) },
                    // review C-02（详情页半边）：M1-14 实装前给反馈，不留死按钮
                    onAddClothing = { showSnack(comingSoonText) },
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
    }
}

/** 类型安全路由从当前栈条目读参（读不到返回 null，不抛）。 */
private inline fun <reified T : Any> NavBackStackEntry.toRouteOrNull(): T? =
    runCatching { toRoute<T>() }.getOrNull()
