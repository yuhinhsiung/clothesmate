package com.yuhinhsiung.clothesmate

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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.feature.overview.ui.OverviewRoute
import com.yuhinhsiung.clothesmate.feature.outfit.ui.OutfitHomeRoute
import com.yuhinhsiung.clothesmate.feature.today.ui.TodayRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.DeleteResult
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDeleteDialog
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDetailRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeEditSheet
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobePickerSheet
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.ClothingDetailRoute
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeRoute
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import kotlinx.coroutines.launch

/**
 * 底部导航 4 Tab —— 开发文档 §6.1、UI 设计文档 §6.1。
 *
 * 图标经 `AppIcons` 集中引用（UI 文档 §5.1 清单，字形核对见
 * `design-system/clothesmate/pages/icon-mapping-override.md`）。
 *
 * P0 阶段选中/未选中暂用同一字形（Rounded），仅靠颜色区分；引入 Outlined 线性变体
 * 属 M1-05 实装范围（见 override 文档 C-04 记录）。
 */
enum class TopLevelDestination(
    val route: String,
    /** 标签文案走资源（review E-a：原先硬编码在枚举里，`tab_*` 四条 string 成死资源） */
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Today(route = "today", labelRes = R.string.tab_today, icon = AppIcons.Today),
    Wardrobe(route = "wardrobe", labelRes = R.string.tab_wardrobe, icon = AppIcons.Wardrobe),
    Outfit(route = "outfit", labelRes = R.string.tab_outfit, icon = AppIcons.Outfit),
    Overview(route = "overview", labelRes = R.string.tab_overview, icon = AppIcons.Overview),
}

/**
 * 显隐规则：仅 4 个一级 Tab 页显示底部导航；二级页（详情 / 编辑 / 录入流程 /
 * 搜索结果 / 选择器）一律隐藏，返回依赖系统返回手势 / 顶部返回按钮。
 * UI 设计文档 §6.1 / 开发计划 P0-06。
 */
private val bottomBarVisibleRoutes: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

fun shouldShowBottomBar(route: String?): Boolean = route in bottomBarVisibleRoutes

@Composable
fun ClothesMateNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
    // 删除 Snackbar 的两条模板（review C-04：占位符，勿用 replace 拼串）。
    // 两模板都带参，故在 Composable 作用域取字符串、参数由调用方提供
    val transferredTpl = stringResource(R.string.wardrobe_delete_snackbar_transferred)
    val deletedTpl = stringResource(R.string.wardrobe_delete_snackbar_deleted)
    fun deleteSnackText(count: Int, target: String?): String =
        if (target != null) transferredTpl.format(count, target) else deletedTpl.format(count)

    /** 当前详情页正在看的衣柜 id（C-12：详情页「更多」菜单需要它定位目标） */
    fun currentWardrobeId(navController: NavHostController): Long =
        navController.currentBackStackEntry?.arguments?.getLong("wardrobeId") ?: 0L

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar(currentRoute)) {
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
                            val selected = backStackEntry?.destination?.hierarchy
                                ?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(destination.route) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Today.route) { TodayRoute() }
            composable(TopLevelDestination.Wardrobe.route) {
                WardrobeRoute(
                    onWardrobeClick = { id ->
                        navController.navigate("wardrobe_detail/$id") {
                            launchSingleTop = true
                        }
                    },
                    onShowAddSheet = { showAddWardrobe = true },
                    onEditWardrobe = { id ->
                        editWardrobeId = id
                        showAddWardrobe = true
                    },
                    onShowDelete = { id -> deleteTargetId = id },
                    // review C-02/C-11：FAB 语义是「+ 录衣服」（§6.4）。
                    // M1-14 未实装，给它一个明确反馈而不是静默死按钮
                    onAddClothing = { showSnack(comingSoonText) },
                )
            }
            composable(TopLevelDestination.Outfit.route) { OutfitHomeRoute() }
            composable(TopLevelDestination.Overview.route) { OverviewRoute() }
            composable(
                route = "wardrobe_detail/{wardrobeId}",
                arguments = listOf(navArgument("wardrobeId") { type = NavType.LongType }),
            ) {
                // review E-c：衣柜 id 由 ViewModel 从 SavedStateHandle 自取，不再经形参
                WardrobeDetailRoute(
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId -> navController.navigate("clothing_detail/$itemId") },
                    // review C-02（详情页半边）：FAB / 空态按钮给明确反馈，不留死按钮
                    onAddClothing = { showSnack(comingSoonText) },
                    // review C-12：详情页「更多」菜单的两个去向
                    onEditWardrobe = {
                        editWardrobeId = currentWardrobeId(navController)
                        showAddWardrobe = true
                    },
                    onDeleteWardrobe = {
                        deleteTargetId = currentWardrobeId(navController)
                    },
                )
            }
            composable(
                route = "clothing_detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) {
                ClothingDetailRoute(
                    onBack = { navController.popBackStack() },
                    onWardrobeClick = { wardrobeId ->
                        navController.navigate("wardrobe_detail/$wardrobeId") {
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
                    // UI 文档 §7.2：保存成功 Snackbar「已添加 · {名称}」
                    // （review U-r：名称由 ViewModel 回传，无需另查库）
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
                    // review C-04：必须走占位符 string resource——原先用
                    // "18 件衣物已移到 $it".replace("18", count) 作用于整串，
                    // 目标柜名里含 18 时会被一起替换掉。
                    showSnack(deleteSnackText(result.count, result.transferredTo))
                    // C-12：删除若是从详情页发起的，衣柜已不存在，退回列表页
                    if (navController.currentDestination?.route?.startsWith("wardrobe_detail") == true) {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
