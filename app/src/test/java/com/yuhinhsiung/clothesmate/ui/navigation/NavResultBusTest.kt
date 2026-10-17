package com.yuhinhsiung.clothesmate.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航结果总线测试 —— review B-01/B-04 修复的直测载体（关闭条件 §9-7「回传链路 ≥2 例」）。
 *
 * 覆盖两条回传链路的完整语义：发布 → 接收方可读 → 消费置空（防陈旧结果复用）。
 */
class NavResultBusTest {

    // 链路 1：衣物选择器 → 穿搭表单（B-01 主实例 + B-19 种子）
    @Test
    fun pickerRoundTripPublishesSeedAndResultWithConsume() {
        val bus = NavResultBus()

        // 进入选择器前的种子（当前已选）
        assertNull(bus.pickerSeed().value)
        bus.publishPickerSeed(listOf(3L, 1L, 2L))
        assertEquals(listOf(3L, 1L, 2L), bus.pickerSeed().value)
        bus.consumePickerSeed()
        assertNull(bus.pickerSeed().value)

        // 「完成」后的最终选择（顺序即选择顺序）
        assertNull(bus.pickedItemIds().value)
        bus.publishPickedItemIds(listOf(1L, 2L, 3L, 9L))
        assertEquals(listOf(1L, 2L, 3L, 9L), bus.pickedItemIds().value)
        bus.consumePickedItemIds()
        assertNull(bus.pickedItemIds().value)
    }

    // 链路 2：编辑保存回执 → 详情页 Snackbar（B-04；衣物与穿搭两个频道独立）
    @Test
    fun savedReceiptsAreIndependentChannels() {
        val bus = NavResultBus()

        assertFalse(bus.clothingSaved().value)
        assertFalse(bus.outfitSaved().value)

        bus.publishClothingSaved()
        assertTrue(bus.clothingSaved().value)
        // 频道隔离：衣物回执不得点亮穿搭回执
        assertFalse(bus.outfitSaved().value)

        bus.consumeClothingSaved()
        assertFalse(bus.clothingSaved().value)

        bus.publishOutfitSaved()
        assertTrue(bus.outfitSaved().value)
        bus.consumeOutfitSaved()
        assertFalse(bus.outfitSaved().value)
    }

    // 链路 3：拍照追加（B-01 三实例表第 3 行）
    @Test
    fun appendCapturedUriRoundTrip() {
        val bus = NavResultBus()

        assertNull(bus.appendCapturedUri().value)
        bus.publishAppendCaptured("file:///cache/captured/capture_1.jpg")
        assertEquals(
            "file:///cache/captured/capture_1.jpg",
            bus.appendCapturedUri().value,
        )
        bus.consumeAppendCaptured()
        assertNull(bus.appendCapturedUri().value)
    }
}