package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMatePreviewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 类别两级选择器 Compose UI 测试 —— 关闭条件 §9-7「1 例 Compose UI 测试」。
 *
 * 直接覆盖 review B-02 的用户可见缺陷：点「上衣」大类 → 二级小类行出现且可选中
 * （旧实现点开后行永不渲染）。测试壳用与生产相同的 [topCategoryChange] 纯函数
 * 驱动状态迁移，验证的是真实逻辑而非仿造。
 */
@RunWith(AndroidJUnit4::class)
class CategorySelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setSelectorContent() {
        composeRule.setContent {
            var state by remember { mutableStateOf(ClothingFormUiState()) }
            ClothesMatePreviewTheme {
                // 必须套 Column：生产页面里组件位于表单 Column 内，两行 chips 纵向排列；
                // 裸放顶层两个 LazyRow 会互相重叠、点击坐标全错（本测试首版即栽在这）
                androidx.compose.foundation.layout.Column {
                    CategorySelector(
                        expandedTopKey = state.expandedTopKey,
                        selectedKey = state.categoryKey,
                        onTopSelect = { top -> state = topCategoryChange(state, top) },
                        onSubSelect = { sub ->
                            state = state.copy(
                                categoryKey = if (state.categoryKey == sub) null else sub,
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun tappingTopCategoryRevealsSubRowAndSubSelectionWorks() {
        setSelectorContent()

        // 二级行初始不存在
        composeRule.onNodeWithText("衬衫").assertDoesNotExist()

        // 点「上衣」→ 二级小类行出现（B-02 核心回归点）
        composeRule.onNodeWithText("上衣").performClick()
        composeRule.onNodeWithText("衬衫").assertExists()
        composeRule.onNodeWithText("毛衣").assertExists()

        // 点「衬衫」→ 选中态落在该 chip 上，且大类保持展开
        composeRule.onNodeWithText("衬衫").performClick()
        composeRule.onNodeWithText("衬衫").assertIsSelected()
    }

    @Test
    fun switchingTopCategorySwapsSubRow() {
        setSelectorContent()

        composeRule.onNodeWithText("上衣").performClick()
        composeRule.onNodeWithText("衬衫").assertExists()

        // 切到「外套」→ 小类行换成外套的小类
        composeRule.onNodeWithText("外套").performClick()
        composeRule.onNodeWithText("衬衫").assertDoesNotExist()
        composeRule.onNodeWithText("夹克").assertExists()

        // 再点「外套」→ 收起
        composeRule.onNodeWithText("外套").performClick()
        composeRule.onNodeWithText("夹克").assertDoesNotExist()
    }

    @Test
    fun singleSubCategorySelectsDirectlyWithoutSubRow() {
        setSelectorContent()

        // 连衣裙：大类即小类，直接定值且不出现二级行
        composeRule.onNodeWithText("连衣裙").performClick()
        composeRule.onNodeWithText("连衣裙").assertIsSelected()
    }
}