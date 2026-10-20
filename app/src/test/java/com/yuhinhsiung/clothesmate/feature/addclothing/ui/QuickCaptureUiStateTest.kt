package com.yuhinhsiung.clothesmate.feature.addclothing.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickCaptureUiStateTest {

    @Test
    fun totalIncludesPendingShotsNotYetInList() {
        val state = QuickCaptureUiState(
            shots = listOf(
                QuickCaptureShot(1, "5", "a", processing = false),
                QuickCaptureShot(2, "6", "b", processing = true),
            ),
            pending = 1,
        )
        assertEquals(3, state.total)
    }

    @Test
    fun codeRangeUsesMinAndMaxCodes() {
        val state = QuickCaptureUiState(
            shots = listOf(
                QuickCaptureShot(1, "9", "a"),
                QuickCaptureShot(2, "7", "b"),
                QuickCaptureShot(3, "12", "c"),
            ),
        )
        assertEquals("7-12", state.codeRange)
    }

    @Test
    fun emptySessionHasNoRange() {
        assertNull(QuickCaptureUiState().codeRange)
        assertEquals(0, QuickCaptureUiState().total)
    }
}
