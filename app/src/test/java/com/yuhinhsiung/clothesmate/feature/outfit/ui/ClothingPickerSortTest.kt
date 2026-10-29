package com.yuhinhsiung.clothesmate.feature.outfit.ui

import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ClothingPickerSortTest {

    @Test
    fun `numeric codes sort by value not lexicographically`() {
        val items = listOf(item(1, "1000"), item(2, "2"), item(3, "10"), item(4, "1"))
        assertEquals(listOf("1", "2", "10", "1000"), sortForPicker(items).map { it.code })
    }

    @Test
    fun `non numeric codes go last and ties use id`() {
        val items = listOf(item(9, "abc"), item(3, "5"), item(2, "5"), item(1, ""))
        assertEquals(listOf(2L, 3L, 1L, 9L), sortForPicker(items).map { it.id })
    }

    @Test
    fun `already ordered list is unchanged`() {
        val items = (1..5).map { item(it.toLong(), it.toString()) }
        assertEquals(items.map { it.id }, sortForPicker(items).map { it.id })
    }

    private fun item(id: Long, code: String) = ClothingItem(
        id = id,
        code = code,
        wardrobeId = 1,
        categoryKey = "top",
        colorKeys = emptyList(),
        styleTags = emptyList(),
        seasonTags = emptyList(),
        originalImagePath = null,
        cutoutImagePath = null,
        brand = null,
        name = null,
        note = null,
        wearCount = 0,
        lastWornAt = null,
        status = "normal",
        createdAt = 0,
        updatedAt = 0,
    )
}
