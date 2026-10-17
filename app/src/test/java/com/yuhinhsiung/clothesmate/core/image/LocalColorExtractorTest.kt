package com.yuhinhsiung.clothesmate.core.image

import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地颜色提取（M2-02）—— 纯 JVM 测试，用像素数组模拟「已知色图」。
 *
 * 口径：聚类主色在 Lab 空间取最近预设色；≥2 个主色各占 ≥20% 判花色。
 */
class LocalColorExtractorTest {

    private fun pixels(color: Int, count: Int): IntArray = IntArray(count) { color }

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `pure red image maps to red`() {
        val result = LocalColorExtractor.extract(pixels(argb(220, 30, 40), 10_000))
        assertEquals(listOf(ClothingColor.Red), result?.colors)
    }

    @Test
    fun `pure blue image maps to blue`() {
        val result = LocalColorExtractor.extract(pixels(argb(37, 99, 235), 10_000))
        assertEquals(listOf(ClothingColor.Blue), result?.colors)
    }

    @Test
    fun `near red still maps to red`() {
        val result = LocalColorExtractor.extract(pixels(argb(200, 30, 39), 10_000))
        assertEquals(listOf(ClothingColor.Red), result?.colors)
    }

    @Test
    fun `white and black halves are judged as multi`() {
        val samples = IntArray(10_000) { index ->
            if (index < 5_000) argb(255, 255, 255) else argb(0, 0, 0)
        }
        val result = LocalColorExtractor.extract(samples)
        assertEquals(listOf(ClothingColor.Multi), result?.colors)
        assertTrue(result!!.clusters.size >= 2)
    }

    @Test
    fun `a small 10 percent accent does not turn a solid color into multi`() {
        val samples = IntArray(10_000) { index ->
            if (index < 9_000) argb(220, 30, 40) else argb(0, 0, 0)
        }
        val result = LocalColorExtractor.extract(samples)
        assertEquals(listOf(ClothingColor.Red), result?.colors)
    }

    @Test
    fun `transparent pixels are ignored so a cutout with transparent background stays red`() {
        val samples = IntArray(10_000) { index ->
            if (index % 2 == 0) argb(220, 30, 40) else argb(0, 0, 0, a = 0)
        }
        val result = LocalColorExtractor.extract(samples)
        assertEquals(listOf(ClothingColor.Red), result?.colors)
    }

    @Test
    fun `fully transparent image returns null`() {
        assertNull(LocalColorExtractor.extract(pixels(argb(0, 0, 0, a = 0), 100)))
    }

    @Test
    fun `white maps to white`() {
        val result = LocalColorExtractor.extract(pixels(argb(255, 255, 255), 5_000))
        assertEquals(listOf(ClothingColor.White), result?.colors)
    }

    @Test
    fun `nearest palette excludes multi`() {
        val nearest = LocalColorExtractor.nearestPaletteColor(argb(219, 39, 119))
        assertEquals(ClothingColor.Pink, nearest)
    }
}
