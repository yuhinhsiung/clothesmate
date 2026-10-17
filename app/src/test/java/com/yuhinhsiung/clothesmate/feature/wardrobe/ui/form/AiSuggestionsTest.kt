package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import com.yuhinhsiung.clothesmate.core.ai.AiRecognition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2-04 验收：**用户手填字段不被覆盖**；「全部采纳」只填未手填项。
 *
 * `applyAiSuggestions` 是纯函数，直接把「手填」与「AI 到达」的各种先后顺序测掉，
 * 不依赖 Compose / ViewModel / Android。
 */
class AiSuggestionsTest {

    private val ai = AiRecognition(
        categoryKey = "shirt",
        styleTags = listOf("通勤", "简约"),
        seasonKeys = listOf("spring", "autumn"),
    )

    @Test
    fun `auto prefill fills empty fields`() {
        val next = applyAiSuggestions(ClothingFormUiState(), ai, onlyEmpty = true)
        assertEquals("shirt", next.categoryKey)
        assertEquals(listOf("通勤", "简约"), next.styleTags)
        assertEquals(listOf("spring", "autumn"), next.seasonKeys)
        assertTrue(next.categoryAiFilled)
        assertTrue(next.styleAiFilled)
        assertTrue(next.seasonAiFilled)
    }

    @Test
    fun `manually chosen category is not overwritten`() {
        val state = ClothingFormUiState(
            categoryKey = "dress",
            touchedFields = setOf(AutoFillField.CATEGORY),
        )
        val next = applyAiSuggestions(state, ai, onlyEmpty = true)
        assertEquals("dress", next.categoryKey)
        assertFalse(next.categoryAiFilled)
    }

    @Test
    fun `manually chosen style and season are not overwritten`() {
        val state = ClothingFormUiState(
            styleTags = listOf("运动"),
            seasonKeys = listOf("winter"),
            touchedFields = setOf(AutoFillField.STYLE, AutoFillField.SEASON),
        )
        val next = applyAiSuggestions(state, ai, onlyEmpty = true)
        assertEquals(listOf("运动"), next.styleTags)
        assertEquals(listOf("winter"), next.seasonKeys)
        assertFalse(next.styleAiFilled)
        assertFalse(next.seasonAiFilled)
    }

    @Test
    fun `accept all only touches fields the user has not filled`() {
        val state = ClothingFormUiState(
            categoryKey = "coat",
            touchedFields = setOf(AutoFillField.CATEGORY),
        )
        val next = applyAiSuggestions(state, ai, onlyEmpty = false)
        // 手填类别保留；未手填的风格/季节被填入
        assertEquals("coat", next.categoryKey)
        assertEquals(listOf("通勤", "简约"), next.styleTags)
        assertEquals(listOf("spring", "autumn"), next.seasonKeys)
    }

    @Test
    fun `empty ai result leaves the form untouched`() {
        val state = ClothingFormUiState()
        val next = applyAiSuggestions(state, AiRecognition(), onlyEmpty = true)
        assertEquals(null, next.categoryKey)
        assertTrue(next.styleTags.isEmpty())
        assertFalse(next.categoryAiFilled)
    }

    @Test
    fun `user edit clears the ai badge but keeps the value`() {
        val aiFilled = applyAiSuggestions(ClothingFormUiState(), ai, onlyEmpty = true)
        val edited = aiFilled.copy(
            styleTags = listOf("运动"),
            styleAiFilled = false,
            touchedFields = aiFilled.touchedFields + AutoFillField.STYLE,
        )
        val next = applyAiSuggestions(edited, ai, onlyEmpty = false)
        assertEquals(listOf("运动"), next.styleTags)
        assertFalse(next.styleAiFilled)
    }
}
