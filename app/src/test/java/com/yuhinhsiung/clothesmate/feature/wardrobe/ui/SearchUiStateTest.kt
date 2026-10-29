package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiStateTest {
    @Test fun `blank default state cannot reset`() {
        assertFalse(SearchUiState().canReset)
    }

    @Test fun `query scope or any attribute enables reset`() {
        assertTrue(SearchUiState(query = "衬衫").canReset)
        assertTrue(SearchUiState(scope = SearchScope.OUTFIT).canReset)
        assertTrue(SearchUiState(categoryFilter = "top").canReset)
        assertTrue(SearchUiState(colorFilter = "red").canReset)
        assertTrue(SearchUiState(styleFilter = "简约").canReset)
    }

    @Test fun `scope remains separate from attribute filter`() {
        val state = SearchUiState(scope = SearchScope.CLOTHING, styleFilter = "通勤")
        assertTrue(state.hasAnyFilter)
        assertTrue(state.canReset)
    }
}
