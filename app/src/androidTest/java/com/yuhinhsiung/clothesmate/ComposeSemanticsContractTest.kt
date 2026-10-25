package com.yuhinhsiung.clothesmate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose 的 clickable 容器会把子 Text / contentDescription 合并进自身语义节点。
 * 这不是 uiautomator 的稳定性保证，因此无障碍问题以 Compose 语义测试为准。
 */
@RunWith(AndroidJUnit4::class)
class ComposeSemanticsContractTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun clickableRowMergesChildText() {
        rule.setContent {
            Row(Modifier.fillMaxWidth().clickable {}) { Text("完整录入") }
        }
        rule.onNodeWithText("完整录入").assertHasClickAction()
    }

    @Test
    fun clickableRowMergesChildDescription() {
        rule.setContent {
            Row(Modifier.fillMaxWidth().clickable {}) {
                Box(Modifier.semantics { contentDescription = "拍照" })
            }
        }
        rule.onNodeWithContentDescription("拍照").assertHasClickAction()
    }

    @Test
    fun navigationItemMergesLabelAndIconDescription() {
        rule.setContent {
            Row {
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = { Box(Modifier.semantics { contentDescription = "今日" }) },
                    label = { Text("今日") },
                )
            }
        }
        rule.onNodeWithText("今日").assertHasClickAction()
    }
}
