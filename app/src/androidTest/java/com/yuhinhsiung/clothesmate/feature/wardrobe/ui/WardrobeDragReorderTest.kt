package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMatePreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 衣柜长按拖拽排序 Compose UI 测试 —— review R-07 的关闭条件
 * 「至少三个衣柜改序后杀进程重进，顺序保持」中**手势与落库**的那一半
 * （「杀进程重进顺序保持」由 WardrobeRepositoryTest 的 persistOrder 用例锁定）。
 *
 * 为什么必须测 UI 层：数据层的 persistOrder 早就有测试且一直通过，但那时
 * **网格卡上根本没有拖拽手势**——数据层绿而用户拖不动。本测试直接驱动真实手势。
 *
 * 注意：这里用的是 `Modifier.pointerInput` + `detectDragGesturesAfterLongPress`，
 * 与卡片自身的 `Card(onClick)` 存在事件竞争——卡片点开详情必须仍然有效，
 * 本测试一并锁住。
 */
@RunWith(AndroidJUnit4::class)
class WardrobeDragReorderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun wardrobe(id: Long, name: String) = Wardrobe(
        id = id,
        name = name,
        note = null,
        coverImagePath = null,
        sortOrder = (id - 1).toInt(),
        createdAt = 0L,
        clothingCount = 0,
    )

    private fun setContent(
        wardrobes: List<Wardrobe>,
        onPersistOrder: (List<Long>) -> Unit = {},
        onClick: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            ClothesMatePreviewTheme {
                WardrobeListScreen(
                    uiState = WardrobeListUiState.Content(wardrobes),
                    onWardrobeClick = onClick,
                    onPersistOrder = onPersistOrder,
                )
            }
        }
    }

    /** 长按 + 拖到目标卡的格子中心；返回前把顺序回调收下来 */
    private fun dragCardOnto(fromLabel: String, toLabel: String) {
        val from = composeRule.onNodeWithText(fromLabel).fetchSemanticsNode().boundsInRoot
        val to = composeRule.onNodeWithText(toLabel).fetchSemanticsNode().boundsInRoot
        val delta = Offset(
            x = to.center.x - from.center.x,
            y = to.center.y - from.center.y,
        )
        composeRule.onNodeWithText(fromLabel).performTouchInput {
            down(center)
            // 先把时间推过长按阈值，手势才算成立
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(delta.x / 2f, delta.y / 2f))
            advanceEventTime(16)
            moveBy(Offset(delta.x / 2f, delta.y / 2f))
            advanceEventTime(16)
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun longPressDragDownMovesCardAndPersistsNewOrder() {
        val persisted = mutableListOf<List<Long>>()
        setContent(
            wardrobes = listOf(wardrobe(1, "A"), wardrobe(2, "B"), wardrobe(3, "C")),
            onPersistOrder = { persisted += it },
        )

        // 把第一张（A）拖到第二行左格（C 的位置）
        dragCardOnto(fromLabel = "A", toLabel = "C")

        assertEquals(
            "松手后必须把重排结果交给持久化（W-08）",
            listOf(listOf(2L, 3L, 1L)),
            persisted,
        )
    }

    @Test
    fun dragIsNoOpWhenCardStaysInItsOwnCell() {
        val persisted = mutableListOf<List<Long>>()
        setContent(
            wardrobes = listOf(wardrobe(1, "A"), wardrobe(2, "B"), wardrobe(3, "C")),
            onPersistOrder = { persisted += it },
        )

        composeRule.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(4f, 4f))
            advanceEventTime(16)
            up()
        }
        composeRule.waitForIdle()

        // 没跨格 → 顺序不变（仍然回调一次，落库内容与原序一致即可）
        assertTrue(
            "原地长按不应改变顺序",
            persisted.isEmpty() || persisted.last() == listOf(1L, 2L, 3L),
        )
    }

    /** 拖拽手势不能把「点卡进详情」吃掉 */
    @Test
    fun plainTapStillOpensWardrobe() {
        val clicked = mutableListOf<Long>()
        setContent(
            wardrobes = listOf(wardrobe(1, "A"), wardrobe(2, "B")),
            onClick = { clicked += it },
        )

        composeRule.onNodeWithText("A").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(1L), clicked)
    }

    /** 长按本身（不拖）不应误触发点击进详情 */
    @Test
    fun longPressWithoutDragDoesNotOpenWardrobe() {
        val clicked = mutableListOf<Long>()
        setContent(
            wardrobes = listOf(wardrobe(1, "A"), wardrobe(2, "B")),
            onClick = { clicked += it },
        )

        composeRule.onNodeWithText("A").performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertTrue("长按属于排序手势，不应顺手把详情打开", clicked.isEmpty())
    }
}