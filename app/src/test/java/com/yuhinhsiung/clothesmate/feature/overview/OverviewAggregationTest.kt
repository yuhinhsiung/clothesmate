package com.yuhinhsiung.clothesmate.feature.overview

import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.feature.overview.ui.OverviewAggregation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewAggregationTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun item(
        id: Long,
        wearCount: Int,
        lastWornAt: Long? = null,
        status: String = ClothingStatus.NORMAL,
        categoryKey: String? = "t_shirt",
        seasonTags: List<String> = listOf("spring"),
        colors: List<String> = listOf("red"),
    ) = ClothingItem(
        id = id,
        code = id.toString(),
        wardrobeId = 1,
        categoryKey = categoryKey,
        colorKeys = colors,
        styleTags = emptyList(),
        seasonTags = seasonTags,
        originalImagePath = null,
        cutoutImagePath = null,
        brand = null,
        name = "item-$id",
        note = null,
        wearCount = wearCount,
        lastWornAt = lastWornAt,
        status = status,
        createdAt = id,
        updatedAt = id,
    )

    @Test
    fun rankingsExcludeStoredAndOrderByCountAndRecency() {
        val state = OverviewAggregation.aggregate(
            clothing = listOf(
                item(1, 5, 100),
                item(2, 9, 100),
                item(3, 0, null),
                item(4, 9, 200),
                item(5, 10, 100, status = ClothingStatus.STORED),
            ),
            wearDates = emptyList(),
            wardrobeCount = 1,
            outfitCount = 0,
            today = today,
        )

        assertEquals(listOf(4L, 2L, 1L), state.topWorn.map { it.item.id })
        assertEquals(listOf(3L), state.leastWorn.map { it.item.id })
        assertFalse(state.topWorn.any { it.item.id == 5L })
    }

    @Test
    fun heatmapCoversThirtyDaysAndCountsMultipleRecordsPerDay() {
        val state = OverviewAggregation.aggregate(
            clothing = listOf(item(1, 0)),
            wearDates = listOf(
                "2026-10-20",
                "2026-10-20",
                "2026-10-20",
                "2026-10-18",
                "2026-09-01",
            ),
            wardrobeCount = 1,
            outfitCount = 0,
            today = today,
        )

        assertEquals(30, state.heatmap.size)
        assertEquals(LocalDate.of(2026, 9, 21), state.heatmap.first().date)
        assertEquals(3, state.heatmap.last().count)
        assertTrue(state.heatmap.last().isToday)
        assertEquals(1, state.heatmap.first { it.date == LocalDate.of(2026, 10, 18) }.count)
        assertEquals(0, state.heatmap.first { it.date == LocalDate.of(2026, 10, 19) }.count)
    }

    @Test
    fun seasonCountsNormalizeBySeasonTagHits() {
        val state = OverviewAggregation.aggregate(
            clothing = listOf(
                item(1, 0, seasonTags = listOf("spring", "autumn")),
                item(2, 0, seasonTags = listOf("autumn")),
                item(3, 0, seasonTags = emptyList()),
            ),
            wearDates = emptyList(),
            wardrobeCount = 1,
            outfitCount = 0,
            today = today,
        )

        assertEquals(3, state.seasonTotal)
        assertEquals(1, state.seasonSlices.first { it.season.key == "spring" }.count)
        assertEquals(2, state.seasonSlices.first { it.season.key == "autumn" }.count)
        assertEquals(0, state.seasonSlices.first { it.season.key == "summer" }.count)
    }

    @Test
    fun categoryRollsUpToTopKeyAndKeepsIncompleteOutOfChart() {
        val state = OverviewAggregation.aggregate(
            clothing = listOf(
                item(1, 0, categoryKey = "t_shirt"),
                item(2, 0, categoryKey = "shirt"),
                item(3, 0, categoryKey = "pants"),
                item(4, 0, categoryKey = null),
            ),
            wearDates = emptyList(),
            wardrobeCount = 1,
            outfitCount = 0,
            today = today,
        )

        assertEquals(2, state.categorySlices.first { it.topKey == "top" }.count)
        assertEquals(1, state.categorySlices.first { it.topKey == "bottom" }.count)
        assertEquals(4, state.clothingCount)
    }
}
