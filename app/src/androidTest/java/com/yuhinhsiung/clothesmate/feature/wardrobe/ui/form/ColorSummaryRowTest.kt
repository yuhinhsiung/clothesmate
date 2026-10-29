package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import org.junit.Rule
import org.junit.Test

class ColorSummaryRowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun empty_shows_placeholder() {
        composeRule.setContent {
            MaterialTheme { ColorSummaryRow(selectedKeys = emptyList(), onClick = {}) }
        }
        composeRule.onNodeWithText("选择颜色").assertIsDisplayed()
    }

    @Test
    fun selected_shows_count() {
        val keys = ClothingColor.entries.take(2).map { it.key }
        composeRule.setContent {
            MaterialTheme { ColorSummaryRow(selectedKeys = keys, onClick = {}) }
        }
        composeRule.onNodeWithText("已选 2 色").assertIsDisplayed()
    }
}
