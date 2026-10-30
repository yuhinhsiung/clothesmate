package com.yuhinhsiung.clothesmate.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** M6-10：穿搭封面取首图、展示名不新增命名输入且原值不清洗。 */
class OutfitModelTest {

    private fun outfit(
        name: String? = null,
        photos: List<String> = emptyList(),
        createdAt: Long = 0L,
    ) = Outfit(
        id = 1L,
        name = name,
        photoPaths = photos,
        styleTags = emptyList(),
        sceneTags = emptyList(),
        seasonTags = emptyList(),
        note = null,
        wearCount = 0,
        lastWornAt = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `cover is the first photo`() {
        val subject = outfit(photos = listOf("outfit/a.jpg", "outfit/b.jpg"))
        assertEquals("outfit/a.jpg", subject.coverPath)
    }

    @Test
    fun `outfit without photos has no cover`() {
        assertNull(outfit(photos = emptyList()).coverPath)
    }

    @Test
    fun `blank name falls back to date only`() {
        val createdAt = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 11, 23, 59, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val name = outfit(name = "   ", createdAt = createdAt).displayName

        assertTrue(name.endsWith(" 的穿搭"))
        assertTrue(name.startsWith("2026-10-11"))
        assertTrue("兜底名不能带时间", !name.contains(":"))
    }

    @Test
    fun `explicit name is kept verbatim`() {
        assertEquals(" 通勤 Look ", outfit(name = " 通勤 Look ").displayName)
    }
}
