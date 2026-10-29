package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleAndSloganAreBothVisible() {
        compose.setContent {
            ClothesMateTheme {
                RootHeader(title = "衣柜", slogan = "给喜欢的衣服，一个位置。")
            }
        }
        compose.onNodeWithText("衣柜").assertIsDisplayed()
        compose.onNodeWithText("给喜欢的衣服，一个位置。").assertIsDisplayed()
    }
}
