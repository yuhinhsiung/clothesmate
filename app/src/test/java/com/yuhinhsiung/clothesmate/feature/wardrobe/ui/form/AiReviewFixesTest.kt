package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R07 / U03 的纯函数回归：
 * - 取消识别是终止态，进程恢复/设置流重发都不能自动复活；
 * - 用户追加自定义风格标签后，不再显示「AI 预填」角标。
 */
class AiReviewFixesTest {

    @Test
    fun cancelledRecognitionIsTerminal() {
        val cancelled = ClothingFormUiState(
            aiDegrade = AiDegradeReason.Cancelled,
        )
        assertTrue(shouldSkipAutoAi(cancelled))
    }

    @Test
    fun freshFormMayAutoStart() {
        assertFalse(shouldSkipAutoAi(ClothingFormUiState()))
    }

    @Test
    fun aiSuggestionsAreTerminal() {
        assertTrue(
            shouldSkipAutoAi(
                ClothingFormUiState(aiSuggestions = AiSuggestions()),
            ),
        )
    }

    @Test
    fun customStyleTagClearsAiBadgeAndDeduplicates() {
        val state = ClothingFormUiState(styleAiFilled = true, styleTags = listOf("简约"))
        val added = state.withCustomStyleTag("街头")
        assertEquals(listOf("简约", "街头"), added.styleTags)
        assertFalse("用户自定义后不得再标成 AI", added.styleAiFilled)

        val duplicate = state.withCustomStyleTag("简约")
        assertEquals(listOf("简约"), duplicate.styleTags)
        assertFalse(duplicate.styleAiFilled)
    }
}
