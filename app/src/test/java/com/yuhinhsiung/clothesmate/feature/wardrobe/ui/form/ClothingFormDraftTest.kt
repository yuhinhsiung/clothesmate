package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-08 / B-18：表单草稿的可持久化快照（SavedStateHandle 里存的就是这段 JSON）。 */
class ClothingFormDraftTest {

    @Test
    fun `round trip preserves fields and ai badges`() {
        val state = ClothingFormUiState(
            imagePath = "/cache/tmp_cutouts/cutout_1.png",
            imageIsCutout = true,
            categoryKey = "shirt",
            expandedTopKey = "top",
            colorKeys = listOf("white"),
            styleTags = listOf("通勤"),
            seasonKeys = listOf("spring"),
            note = "旧衬衫",
            wardrobeId = 7L,
            wardrobeName = "主卧",
            code = "102",
            codeTouched = true,
            userModified = true,
            touchedFields = setOf(AutoFillField.CATEGORY),
            aiPhase = AiPhase.Prefilled,
            aiSuggestions = AiSuggestions("shirt", listOf("通勤"), listOf("spring")),
            categoryAiFilled = false,
            styleAiFilled = true,
            seasonAiFilled = true,
            colorsAutoFilled = true,
        )

        val raw = ClothingFormDraft.encode(state, "/cache/tmp_images/staged_1.jpg", "/cache/tmp_cutouts/cutout_1.png")
        val restored = ClothingFormDraft.decode(raw)?.toUiState(ClothingFormUiState(isEdit = false))

        assertEquals("shirt", restored?.categoryKey)
        assertEquals("top", restored?.expandedTopKey)
        assertEquals(listOf("white"), restored?.colorKeys)
        assertEquals(setOf(AutoFillField.CATEGORY), restored?.touchedFields)
        assertEquals(AiPhase.Prefilled, restored?.aiPhase)
        assertEquals("shirt", restored?.aiSuggestions?.categoryKey)
        assertTrue(restored!!.styleAiFilled)
        assertTrue(restored.colorsAutoFilled)
        assertEquals("102", restored.code)
        assertTrue(restored.userModified)
    }

    @Test
    fun `running phase is restored as prefilled when suggestions exist`() {
        val draft = ClothingFormDraft(
            aiPhase = AiPhase.Running.name,
            aiSuggestions = AiSuggestions("shirt", emptyList(), emptyList()),
        )
        val restored = draft.toUiState(ClothingFormUiState())
        assertEquals(AiPhase.Prefilled, restored.aiPhase)
    }

    @Test
    fun `waiting consent restores the dialog`() {
        val draft = ClothingFormDraft(
            aiPhase = AiPhase.WaitingConsent.name,
            privacyEndpointHost = "api.deepseek.com",
        )
        val restored = draft.toUiState(ClothingFormUiState())
        assertTrue(restored.showPrivacyDialog)
        assertEquals("api.deepseek.com", restored.privacyEndpointHost)
    }

    @Test
    fun `corrupt or missing json decodes to null`() {
        assertNull(ClothingFormDraft.decode(""))
        assertNull(ClothingFormDraft.decode(null))
        assertNull(ClothingFormDraft.decode("{ not json"))
    }
}
