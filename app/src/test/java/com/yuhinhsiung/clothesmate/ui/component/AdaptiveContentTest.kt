package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 宽屏断点：手机全宽、medium 600dp、expanded 840dp（M5-07）。 */
class AdaptiveContentTest {

    @Test
    fun `compact width keeps full width`() {
        assertEquals(400.dp, adaptiveContentWidth(400.dp))
    }

    @Test
    fun `medium width is capped at 600`() {
        assertEquals(600.dp, adaptiveContentWidth(700.dp))
    }

    @Test
    fun `expanded width is capped at 840`() {
        assertEquals(840.dp, adaptiveContentWidth(1000.dp))
    }
}
