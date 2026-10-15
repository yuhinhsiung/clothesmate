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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    val label: String,
    val icon: ImageVector,
) {
    Today(route = "today", label = "今日", icon = AppIcons.Today),
    Wardrobe(route = "wardrobe", label = "衣柜", icon = AppIcons.Wardrobe),
    Outfit(route = "outfit", label = "穿搭", icon = AppIcons.Outfit),
    Overview(route = "overview", label = "概览", icon = AppIcons.Overview),
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

    // 衣柜相关弹层状态（M1-06/07/08）
    var showAddWardrobe by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }
    var showWardrobePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
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
                    onWardrobeClick = { id -> navController.navigate("wardrobe_detail/$id") },
                    onShowAddSheet = { showAddWardrobe = true },
                    onShowDelete = { id -> deleteTargetId = id },
                )
            }
            composable(TopLevelDestination.Outfit.route) { OutfitHomeRoute() }
            composable(TopLevelDestination.Overview.route) { OverviewRoute() }
            composable(
                route = "wardrobe_detail/{wardrobeId}",
                arguments = listOf(navArgument("wardrobeId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("wardrobeId") ?: 0L
                WardrobeDetailRoute(
                    wardrobeId = id,
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId -> navController.navigate("clothing_detail/$itemId") },
                )
            }
            composable(
                route = "clothing_detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) {
                ClothingDetailRoute(
                    onBack = { navController.popBackStack() },
                    onWardrobeClick = { wardrobeId ->
                        navController.navigate("wardrobe_detail/$wardrobeId")
                    },
                )
            }
        }

        // ── 衣柜相关弹层（M1-06/07/08） ──────────────────────────
        if (showAddWardrobe) {
            WardrobeEditSheet(
                onDismiss = { showAddWardrobe = false },
                onSaved = { _ ->
                    showAddWardrobe = false
                    // UI 文档 §7.2：保存成功 Snackbar「已添加 · {名称}」。
                    // 名称由 ViewModel 持有，这里读不到，故用通用文案；
                    // 精确到名称的提示见 M1-14 走「返回列表即刷新」链路时补。
                    scope.launch {
                        snackbarHostState.showSnackbar("衣柜已保存")
                    }
                },
            )
        }

        deleteTargetId?.let { id ->
            WardrobeDeleteDialog(
                wardrobeId = id,
                onDismiss = { deleteTargetId = null },
                onDeleted = { result: DeleteResult ->
                    deleteTargetId = null
                    // UI 文档 §7.2：执行后 Snackbar 告知去向
                    val message = result.transferredTo
                        ?.let { "18 件衣物已移到 $it" }
                        ?.replace("18", result.count.toString())
                        ?: "${result.count} 件衣物已删除"
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                },
            )
        }
    }
}
