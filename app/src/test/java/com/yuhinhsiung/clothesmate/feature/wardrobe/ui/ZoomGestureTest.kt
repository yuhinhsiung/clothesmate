package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 衣物详情大图缩放/平移的**数值**测试 —— review R-09 修复的直测载体。
 *
 * 手势层（`awaitEachGesture`）只负责把事件喂进 [applyGesture]，判断全在这里，
 * 因此缩放上限、回中、夹紧这些容易写错的语义可以直接在 JVM 上钉住。
 * 「1× 单指不消费事件、页面照常滚动」是手势层的结构约束，见
 * [ZoomableImage] 的 KDoc 与真机走查记录。
 */
class ZoomGestureTest {

    private val container = IntSize(1000, 1250)

    @Test
    fun doubleFingerZoomInScalesUp() {
        val zoomed = ZoomState().applyGesture(zoomChange = 2f, panChange = Offset.Zero, container = container)
        assertEquals(2f, zoomed.scale, 0.001f)
        assertTrue(zoomed.isZoomed)
    }

    @Test
    fun scaleIsClampedToMax() {
        val zoomed = ZoomState(scale = 3f).applyGesture(zoomChange = 5f, Offset.Zero, container)
        assertEquals(MAX_ZOOM, zoomed.scale, 0.001f)
    }

    @Test
    fun singleFingerPanAtRestKeepsStateUnchanged() {
        // 1× 时单指拖动：zoomChange 恒为 1、pan 有值——不得产生任何位移，
        // 否则页面纵向滚动一经过图片就会被"卡"住
        val state = ZoomState().applyGesture(1f, Offset(120f, 300f), container)
        assertEquals(MIN_ZOOM, state.scale, 0.001f)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed)
    }

    @Test
    fun panWhileZoomedIsClampedToOverflowBounds() {
        val zoomed = ZoomState(scale = 2f).applyGesture(1f, Offset(9999f, 9999f), container)
        // 放大 2 倍时单边最多能拖出容器的一半
        assertEquals(500f, zoomed.offset.x, 0.001f)
        assertEquals(625f, zoomed.offset.y, 0.001f)
    }

    @Test
    fun zoomingBackOutResetsPanToCenter() {
        val zoomed = ZoomState(scale = 2f).applyGesture(1f, Offset(300f, 300f), container)
        assertTrue(zoomed.offset != Offset.Zero)

        val back = zoomed.applyGesture(zoomChange = 0.1f, panChange = Offset.Zero, container = container)
        assertEquals(MIN_ZOOM, back.scale, 0.001f)
        assertEquals("缩回 1× 必须回中，否则再放大时画面停在上次拖到的位置", Offset.Zero, back.offset)
    }

    @Test
    fun clampIsNoOpBeforeContainerIsMeasured() {
        val offset = clampOffset(Offset(50f, 50f), IntSize.Zero, 2f)
        assertEquals(Offset.Zero, offset)
    }
}