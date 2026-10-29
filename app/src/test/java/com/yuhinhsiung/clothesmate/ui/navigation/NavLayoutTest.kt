package com.yuhinhsiung.clothesmate.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NavLayoutTest {
    @Test fun `599dp keeps bottom bar`() = assertEquals(NavLayout.BOTTOM_BAR, navLayoutFor(599.dp))
    @Test fun `600dp switches to rail`() = assertEquals(NavLayout.RAIL, navLayoutFor(600.dp))
    @Test fun `840dp stays rail`() = assertEquals(NavLayout.RAIL, navLayoutFor(840.dp))
}
