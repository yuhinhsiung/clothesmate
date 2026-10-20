package com.yuhinhsiung.clothesmate.feature.overview.ui

import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.Season
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 概览进阶版聚合 —— M4-01 / 开发文档 §2.5.2、UI 文档 §7.5。
 *
 * 纯 Kotlin（只依赖 java.time），便于 JVM 单测；不读数据库、不依赖 Android。
 * 口径：
 * - 榜单只统计**正常状态**衣物（收起是主动收纳，不应被当成闲置）；
 * - Top 榜按穿着次数降序，同次数按最近穿着降序；
 * - 压箱底按穿着次数升序，从未穿的优先，再按创建时间升序；
 * - 热力图固定最近 30 天（含今天），无记录日期补 0；
 * - 季节占比按季节标签命中次数归一化（一件衣物可有多季）。
 */
object OverviewAggregation {

    private const val RANK_SIZE = 5
    private const val HEATMAP_DAYS = 30

    fun aggregate(
        clothing: List<ClothingItem>,
        wearDates: List<String>,
        wardrobeCount: Int,
        outfitCount: Int,
        today: LocalDate = LocalDate.now(),
    ): OverviewUiState {
        val byTop = clothing
            .mapNotNull { item -> item.categoryKey?.let { ClothingCategory.fromKey(it)?.topKey } }
            .groupingBy { it }
            .eachCount()
        val categorySlices = byTop.entries
            .map { (topKey, count) ->
                CategorySlice(
                    topKey = topKey,
                    topName = ClothingCategory.entries.first { it.topKey == topKey }.topDisplayName,
                    count = count,
                )
            }
            .sortedWith(compareByDescending<CategorySlice> { it.count }.thenBy { it.topName })

        val colorCounts = ClothingColor.entries
            .map { color -> ColorCount(color, clothing.count { color.key in it.colorKeys }) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }

        val normal = clothing.filter { it.status == ClothingStatus.NORMAL }
        val topWorn = normal
            .filter { it.wearCount > 0 }
            .sortedWith(
                compareByDescending<ClothingItem> { it.wearCount }
                    .thenByDescending { it.lastWornAt ?: Long.MIN_VALUE }
                    .thenBy { it.id },
            )
            .take(RANK_SIZE)
            .map { RankedClothing(it) }

        val leastWorn = normal
            .filterNot { item -> topWorn.any { it.item.id == item.id } }
            .sortedWith(
                compareBy<ClothingItem> { it.wearCount }
                    .thenBy { it.lastWornAt ?: Long.MIN_VALUE }
                    .thenBy { it.createdAt }
                    .thenBy { it.id },
            )
            .take(RANK_SIZE)
            .map { RankedClothing(it) }

        val start = today.minusDays((HEATMAP_DAYS - 1).toLong())
        val dates = wearDates.mapNotNull { raw ->
            try {
                LocalDate.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }.filter { !it.isBefore(start) && !it.isAfter(today) }
        val countByDate = dates.groupingBy { it }.eachCount()
        val heatmap = (0 until HEATMAP_DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            HeatmapDay(date = date, count = countByDate[date] ?: 0, isToday = date == today)
        }

        val seasonCounts = Season.entries.map { season ->
            SeasonSlice(
                season = season,
                count = clothing.count { item ->
                    item.seasonTags.any { tag ->
                        tag == season.key || tag == season.displayName
                    }
                },
            )
        }

        // M4-01 兼容：旧数据只有 wearCount、没有 wear_record；无法还原日期，明确提示不计入热力图。
        val legacyWearCount = if (wearDates.isEmpty()) {
            normal.filter { it.wearCount > 0 }.sumOf { it.wearCount }
        } else {
            0
        }

        return OverviewUiState(
            loading = false,
            wardrobeCount = wardrobeCount,
            clothingCount = clothing.size,
            outfitCount = outfitCount,
            categorySlices = categorySlices,
            colorCounts = colorCounts,
            topWorn = topWorn,
            leastWorn = leastWorn,
            heatmap = heatmap,
            heatmapStart = start,
            seasonSlices = seasonCounts,
            legacyWearCount = legacyWearCount,
        )
    }
}

data class RankedClothing(val item: ClothingItem)

data class HeatmapDay(
    val date: LocalDate,
    val count: Int,
    val isToday: Boolean,
)

data class SeasonSlice(
    val season: Season,
    val count: Int,
)
