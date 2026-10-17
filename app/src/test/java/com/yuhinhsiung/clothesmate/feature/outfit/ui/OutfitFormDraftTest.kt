package com.yuhinhsiung.clothesmate.feature.outfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M2-08 / B-18：穿搭表单草稿快照。 */
class OutfitFormDraftTest {

    @Test
    fun `round trip preserves selection and tags`() {
        val state = OutfitFormUiState(
            isEdit = false,
            existingPaths = emptyList(),
            selectedItemIds = listOf(3L, 1L, 2L),
            selectedCategoryIds = setOf(9L, 8L),
            styleTags = listOf("通勤"),
            sceneTags = listOf("会议"),
            seasonTags = listOf("autumn"),
            note = "周一",
            baseline = OutfitFormBaseline(),
        )

        val raw = OutfitFormDraft.encode(state, listOf("/cache/tmp_images/staged_3.jpg"))
        val draft = OutfitFormDraft.decode(raw)
        val restored = draft?.toUiState(
            base = OutfitFormUiState(),
            stagedPaths = listOf("/cache/tmp_images/staged_3.jpg"),
        )

        assertEquals(listOf(3L, 1L, 2L), restored?.selectedItemIds)
        assertEquals(setOf(9L, 8L), restored?.selectedCategoryIds)
        assertEquals(listOf("通勤"), restored?.styleTags)
        assertEquals(listOf("/cache/tmp_images/staged_3.jpg"), restored?.stagedDisplayPaths)
        assertEquals("周一", restored?.note)
    }

    @Test
    fun `dirty state is computed against the restored baseline`() {
        val state = OutfitFormUiState(
            selectedItemIds = listOf(1L),
            baseline = OutfitFormBaseline(selectedItemIds = listOf(1L)),
        )
        val restored = OutfitFormDraft.decode(OutfitFormDraft.encode(state, emptyList()))!!
            .toUiState(OutfitFormUiState(), emptyList())
        // 恢复后与 baseline 一致 → 不算用户改动
        assertEquals(false, restored.userModified)
    }

    @Test
    fun `corrupt draft decodes to null`() {
        assertNull(OutfitFormDraft.decode("nope"))
    }
}
