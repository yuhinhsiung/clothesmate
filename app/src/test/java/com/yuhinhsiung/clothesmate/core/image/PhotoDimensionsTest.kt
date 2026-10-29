package com.yuhinhsiung.clothesmate.core.image

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoDimensionsTest {
    @Test
    fun portraitRatio() {
        assertEquals(0.75f, PhotoDimensions(1200, 1600).aspectRatio, 0.0001f)
    }

    @Test
    fun landscapeRatio() {
        assertEquals(1.5f, PhotoDimensions(2400, 1600).aspectRatio, 0.0001f)
    }

    @Test
    fun invalidDimensionsFallBackToThreeFour() {
        assertEquals(0.75f, PhotoDimensions(0, 0).aspectRatio, 0.0001f)
        assertEquals(0.75f, PhotoDimensions(-1, 10).aspectRatio, 0.0001f)
    }
}
