package com.yuhinhsiung.clothesmate.core.recommendation

import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.model.OutfitWithItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecommendationEngineTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 19)

    @Test
    fun `temperature and schedule and style and never worn score exactly`() {
        val outfit = outfit(
            id = 1,
            sceneTags = listOf("通勤", "会议"),
            seasonTags = listOf("summer"),
            styleTags = listOf("简约"),
        )

        val result = RecommendationEngine.recommend(
            input(
                outfits = listOf(outfit),
                weather = WeatherFacts(minTemp = 30, maxTemp = 34, phenomenon = "晴"),
                schedule = setOf("通勤", "会议"),
                style = "简约",
            ),
        ).single()

        // 温度 +3、行程 2 个 +4、风格 +1、从未穿过 +2
        assertEquals(10, result.score)
        assertTrue(result.reasons.any { it is RecommendationReason.Temperature })
        assertTrue(result.reasons.any { it is RecommendationReason.NeverWorn })
        assertEquals(2, result.reasons.count { it is RecommendationReason.Schedule })
    }

    @Test
    fun `weather unavailable skips temperature and phenomenon rules`() {
        val outfit = outfit(
            id = 1,
            sceneTags = listOf("通勤"),
            seasonTags = listOf("summer"),
            lastWornAt = daysAgo(3),
        )

        val result = RecommendationEngine.recommend(
            input(outfits = listOf(outfit), weather = null, schedule = setOf("通勤")),
        ).single()

        // 只有行程 +2 与 30 天内 -2；温度 / 天气现象必须完全跳过。
        assertEquals(0, result.score)
        assertFalse(result.reasons.any { it is RecommendationReason.Temperature })
        assertTrue(result.reasons.any { it is RecommendationReason.RecentlyWorn })
    }

    @Test
    fun `rain hard excludes long skirt and rain ready note adds one`() {
        val longSkirt = outfit(
            id = 1,
            items = listOf(
                item(1, ClothingCategory.TShirt.key),
                item(2, ClothingCategory.SkirtLong.key),
            ),
        )
        val rainReady = outfit(
            id = 2,
            note = "这件外套防雨",
            items = listOf(item(3, ClothingCategory.Jacket.key)),
        )
        val plain = outfit(
            id = 3,
            items = listOf(item(4, ClothingCategory.Hoodie.key)),
        )

        val result = RecommendationEngine.recommend(
            input(
                outfits = listOf(longSkirt, rainReady, plain),
                weather = WeatherFacts(minTemp = 20, maxTemp = 22, phenomenon = "小雨"),
            ),
        )

        assertTrue(result.none { it.outfit.outfit.id == 1L })
        val ready = result.single { it.outfit.outfit.id == 2L }
        val plainScore = result.single { it.outfit.outfit.id == 3L }
        assertEquals(plainScore.score + 1, ready.score)
    }

    @Test
    fun `stored item and incomplete outfit are excluded`() {
        val stored = outfit(
            id = 1,
            items = listOf(item(1, ClothingCategory.TShirt.key, status = ClothingStatus.STORED)),
        )
        val incomplete = outfit(
            id = 2,
            items = listOf(item(2, ClothingCategory.TShirt.key)),
            referencedItemCount = 2,
        )
        val normal = outfit(id = 3, items = listOf(item(3, ClothingCategory.TShirt.key)))

        val result = RecommendationEngine.recommend(
            input(outfits = listOf(stored, incomplete, normal)),
        )

        assertEquals(listOf(3L), result.map { it.outfit.outfit.id })
    }

    @Test
    fun `recently worn within thirty days gets minus two and older does not`() {
        val recent = outfit(
            id = 1,
            items = listOf(item(1, ClothingCategory.TShirt.key)),
            lastWornAt = daysAgo(29),
        )
        val old = outfit(
            id = 2,
            items = listOf(item(2, ClothingCategory.TShirt.key)),
            lastWornAt = daysAgo(30),
        )

        val result = RecommendationEngine.recommend(input(outfits = listOf(recent, old)))

        assertEquals(-2, result.single { it.outfit.outfit.id == 1L }.score)
        assertEquals(0, result.single { it.outfit.outfit.id == 2L }.score)
    }

    @Test
    fun `returns at most five and sorts by score desc`() {
        val outfits = (1L..6L).map { id ->
            outfit(id = id, items = listOf(item(id, ClothingCategory.TShirt.key)))
        }
        val result = RecommendationEngine.recommend(input(outfits = outfits))
        assertEquals(5, result.size)
        assertEquals(result.map { it.score }, result.map { it.score }.sortedDescending())
    }

    @Test
    fun `batch cycles through all candidates and wraps to first`() {
        val entries = (1L..4L).map { id ->
            RecommendedOutfit(
                outfit = outfit(id = id, items = listOf(item(id, ClothingCategory.TShirt.key))),
                score = 0,
                reasons = emptyList(),
            )
        }
        val batch = RecommendationBatch(entries, batchSize = 3)

        assertEquals(listOf(1L, 2L, 3L), batch.current().entries.map { it.outfit.outfit.id })
        val second = batch.next()
        assertEquals(listOf(4L), second.entries.map { it.outfit.outfit.id })
        assertFalse(second.wrappedToFirst)

        val third = batch.next()
        assertEquals(listOf(1L, 2L, 3L), third.entries.map { it.outfit.outfit.id })
        assertTrue(third.wrappedToFirst)
    }

    @Test
    fun `batch of three shows exhausted without changing order`() {
        val entries = (1L..3L).map { id ->
            RecommendedOutfit(
                outfit = outfit(id = id, items = listOf(item(id, ClothingCategory.TShirt.key))),
                score = 0,
                reasons = emptyList(),
            )
        }
        val batch = RecommendationBatch(entries, batchSize = 3)
        val next = batch.next()
        assertEquals(listOf(1L, 2L, 3L), next.entries.map { it.outfit.outfit.id })
        assertTrue(next.wrappedToFirst)
    }

    @Test
    fun `fallback is seeded, uses normal clothing, and need not be unused by outfits`() {
        val all = listOf(
            item(1, ClothingCategory.TShirt.key),
            item(2, ClothingCategory.Shorts.key),
            item(3, ClothingCategory.Sandals.key),
            item(4, ClothingCategory.Jacket.key, status = ClothingStatus.STORED),
        )
        val input = input(
            outfits = emptyList(),
            weather = WeatherFacts(minTemp = 30, maxTemp = 34, phenomenon = "晴"),
            allClothing = all,
        )

        val first = buildFallbackCombination(input, seed = 7L)
        val second = buildFallbackCombination(input, seed = 7L)

        assertNotNull(first)
        assertEquals(first!!.items.map { it.id }, second!!.items.map { it.id })
        assertTrue(first.items.all { it.status == ClothingStatus.NORMAL })
        assertEquals(3, first.items.size)
        assertTrue(first.items.none { it.id == 4L })
    }

    @Test
    fun `fallback returns null when fewer than two normal items`() {
        val input = input(
            outfits = emptyList(),
            weather = null,
            allClothing = listOf(item(1, ClothingCategory.TShirt.key)),
        )
        assertNull(buildFallbackCombination(input, seed = 1L))
    }

    @Test
    fun `temperature boundaries follow continuous ranges`() {
        assertEquals(
            com.yuhinhsiung.clothesmate.core.model.Season.Summer,
            seasonForTemperature(WeatherFacts(26, 26, "晴")),
        )
        assertEquals(
            com.yuhinhsiung.clothesmate.core.model.Season.Spring,
            seasonForTemperature(WeatherFacts(16, 24, "晴")),
        )
        assertEquals(
            com.yuhinhsiung.clothesmate.core.model.Season.Autumn,
            seasonForTemperature(WeatherFacts(8, 15, "晴")),
        )
        assertEquals(
            com.yuhinhsiung.clothesmate.core.model.Season.Winter,
            seasonForTemperature(WeatherFacts(-2, 7, "晴")),
        )
    }

    private fun input(
        outfits: List<OutfitWithItems>,
        weather: WeatherFacts? = null,
        schedule: Set<String> = emptySet(),
        style: String? = null,
        allClothing: List<ClothingItem> = emptyList(),
    ) = RecommendationInput(
        outfits = outfits,
        weather = weather,
        scheduleTags = schedule,
        today = today,
        stylePreference = style,
        allClothing = allClothing,
    )

    private fun daysAgo(days: Long): Long =
        today.minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun outfit(
        id: Long,
        sceneTags: List<String> = emptyList(),
        seasonTags: List<String> = emptyList(),
        styleTags: List<String> = emptyList(),
        note: String? = null,
        lastWornAt: Long? = null,
        items: List<ClothingItem> = listOf(item(id * 100, ClothingCategory.TShirt.key)),
        referencedItemCount: Int = items.size,
    ) = OutfitWithItems(
        outfit = Outfit(
            id = id,
            name = "穿搭 $id",
            photoPaths = emptyList(),
            styleTags = styleTags,
            sceneTags = sceneTags,
            seasonTags = seasonTags,
            note = note,
            wearCount = 0,
            lastWornAt = lastWornAt,
            createdAt = 0L,
            updatedAt = 0L,
            itemCount = items.size,
        ),
        items = items,
        referencedItemCount = referencedItemCount,
    )

    private fun item(
        id: Long,
        categoryKey: String,
        status: String = ClothingStatus.NORMAL,
    ) = ClothingItem(
        id = id,
        code = id.toString(),
        wardrobeId = 1L,
        categoryKey = categoryKey,
        colorKeys = listOf("black"),
        styleTags = emptyList(),
        seasonTags = emptyList(),
        originalImagePath = null,
        cutoutImagePath = null,
        brand = null,
        name = null,
        note = null,
        wearCount = 0,
        lastWornAt = null,
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
