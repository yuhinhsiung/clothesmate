package com.yuhinhsiung.clothesmate.core.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldAvoidanceTest {

    @Test
    fun noSeparatingFoldDoesNotChangeLayout() {
        assertEquals(FoldPadding(), foldAvoidance(false, true, 500, 520, 1000, 2000, 2f))
    }

    @Test
    fun verticalHingeKeepsContentInLeftPane() {
        // 2000px / 2dpi = 1000dp; hinge 20px at center leaves 495dp per pane.
        val fold = foldAvoidance(true, true, 990, 1010, 2000, 2000, 2f)
        assertEquals(0, fold.startDp)
        assertEquals(505, fold.endDp)
        assertEquals(495, fold.effectiveWidthDp)
    }

    @Test
    fun verticalHingeUsesRightPaneWhenLeftIsTooNarrow() {
        val fold = foldAvoidance(true, true, 600, 620, 2000, 2000, 2f)
        assertEquals(310, fold.startDp)
        assertEquals(690, fold.effectiveWidthDp)
    }

    @Test
    fun horizontalHingeKeepsContentInTopPane() {
        val fold = foldAvoidance(true, false, 800, 820, 1000, 2000, 2f)
        assertEquals(0, fold.topDp)
        assertEquals(600, fold.bottomDp)
    }

    @Test
    fun bothPanesTooSmallFallsBackToFullWindow() {
        val fold = foldAvoidance(true, true, 300, 320, 500, 2000, 2f)
        assertEquals(FoldPadding(), fold)
    }

    @Test
    fun invalidDensityIsIgnored() {
        assertEquals(FoldPadding(), foldAvoidance(true, true, 0, 0, 1000, 2000, 0f))
    }
}
