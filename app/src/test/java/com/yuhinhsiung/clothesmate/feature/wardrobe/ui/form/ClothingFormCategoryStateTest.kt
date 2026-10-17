package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 类别两级选择的状态迁移测试 —— review B-02 修复的直测载体（关闭条件 §9-2）。
 *
 * 旧缺陷：多小类大类点开即把值置空，而二级行渲染由值反推 → 8 个大类里 6 个
 * 「点开无反应」。现展开态独立（expandedTopKey），本测试锁定迁移语义。
 */
class ClothingFormCategoryStateTest {

    @Test
    fun multiSubTopExpandsRowWithoutTouchingValue() {
        val state = topCategoryChange(ClothingFormUiState(), "top")

        // 展开 上衣（5 个小类），值留空待选小类——这是旧实现丢失的关键状态
        assertEquals("top", state.expandedTopKey)
        assertNull(state.categoryKey)
    }

    @Test
    fun singleSubTopSetsValueDirectly() {
        // 连衣裙：大类即小类，直接定值（无二级行）
        val state = topCategoryChange(ClothingFormUiState(), "dress")
        assertEquals("dress", state.expandedTopKey)
        assertEquals("dress", state.categoryKey)

        // 帽子同理
        val hat = topCategoryChange(ClothingFormUiState(), "hat")
        assertEquals("hat", hat.categoryKey)
    }

    @Test
    fun switchingTopClearsValueFromAnotherTop() {
        val withShirt = ClothingFormUiState(categoryKey = "shirt", expandedTopKey = "top")

        // 切到 外套：衬衫值不属于外套 → 清空待选
        val switched = topCategoryChange(withShirt, "outer")
        assertEquals("outer", switched.expandedTopKey)
        assertNull(switched.categoryKey)
    }

    @Test
    fun retapExpandedTopCollapsesAndKeepsValue() {
        val withShirt = ClothingFormUiState(categoryKey = "shirt", expandedTopKey = "top")

        // 再点同一大类 = 收起小类行，已选值保留
        val collapsed = topCategoryChange(withShirt, "top")
        assertNull(collapsed.expandedTopKey)
        assertEquals("shirt", collapsed.categoryKey)
    }

    @Test
    fun expandingSameTopAgainKeepsItsValue() {
        val withShirt = ClothingFormUiState(categoryKey = "shirt", expandedTopKey = "outer")

        // 从别处回到已选值所属大类：值保留（同属判定）
        val back = topCategoryChange(withShirt, "top")
        assertEquals("top", back.expandedTopKey)
        assertEquals("shirt", back.categoryKey)
    }
}