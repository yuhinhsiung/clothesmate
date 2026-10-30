package com.yuhinhsiung.clothesmate.feature.outfit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** M6-10：穿搭照片 0–9 张硬上限，已存在与已暂存都占位。 */
class OutfitPhotoLimitTest {

    @Test
    fun `empty form accepts nine photos`() {
        assertEquals(9, remainingPhotoSlots(existingCount = 0, stagedCount = 0))
    }

    @Test
    fun `eighth photo leaves one slot`() {
        assertEquals(1, remainingPhotoSlots(existingCount = 8, stagedCount = 0))
    }

    @Test
    fun `nine existing photos leave no slot`() {
        assertEquals(0, remainingPhotoSlots(existingCount = 9, stagedCount = 0))
    }

    @Test
    fun `existing and staged photos share the cap`() {
        assertEquals(0, remainingPhotoSlots(existingCount = 2, stagedCount = 8))
        assertEquals(2, remainingPhotoSlots(existingCount = 2, stagedCount = 5))
    }

    @Test
    fun `counts over cap clamp to zero`() {
        assertEquals(0, remainingPhotoSlots(existingCount = 10, stagedCount = 0))
    }
}
