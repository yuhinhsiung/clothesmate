package com.yuhinhsiung.clothesmate.core.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSignalsTest {

    @Test
    fun physicalWidthConvertsToLayoutDp() {
        assertEquals(411, windowWidthDp(widthPx = 1080, density = 2.625f))
        assertEquals(600, windowWidthDp(widthPx = 1800, density = 3f))
    }

    @Test
    fun roundingUsesNearestDp() {
        assertEquals(393, windowWidthDp(widthPx = 1080, density = 2.75f))
    }

    @Test
    fun invalidDensityIsTreatedAsZero() {
        assertEquals(0, windowWidthDp(widthPx = 1080, density = 0f))
        assertEquals(0, windowWidthDp(widthPx = 1080, density = -1f))
    }
}
