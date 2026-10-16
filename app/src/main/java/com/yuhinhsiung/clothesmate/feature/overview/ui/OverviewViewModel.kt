package com.yuhinhsiung.clothesmate.feature.overview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 概览页 ViewModel —— M1-22 / 开发文档 §2.5.2 基础版、UI 文档 §7.5。
 *
 * **口径**（M1-22 验收）：衣柜数 = 用户自建总数（无内置衣柜）；衣物 / 穿搭
 * **不含软删除**（衣物走 observeCount / 穿搭本身无软删除）；类别分布按大类聚合
 * （环形图 8 扇区封顶，小类聚合到大类，避免 24 片碎图）；颜色分布用衣物真实颜色。
 * M1 静态展示，点击交互归 M4-01。
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    wardrobeDao: WardrobeDao,
    clothingDao: ClothingItemDao,
    outfitDao: OutfitDao,
) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> = combine(
        wardrobeDao.observeCount(),
        clothingDao.observeAll(),
        outfitDao.observeCount(),
    ) { wardrobeCount, clothingEntities, outfitCount ->
        val clothing = clothingEntities.map { it.toDomain() }

        // 类别分布：小类 key → 大类聚合（无类别的「待完善」衣物不入图，中心总件数仍全量）
        val byTop = clothing
            .mapNotNull { item -> item.categoryKey?.let { ClothingCategory.fromKey(it)?.topKey } }
            .groupingBy { it }
            .eachCount()
        val slices = byTop.entries
            .map { (topKey, count) ->
                CategorySlice(
                    topKey = topKey,
                    topName = ClothingCategory.entries.first { it.topKey == topKey }.topDisplayName,
                    count = count,
                )
            }
            .sortedByDescending { it.count }

        // 颜色分布：衣物真实颜色计数（多色衣物在每个命中的颜色里各计 1 次；0 计数不展示）
        val colorCounts = ClothingColor.entries
            .map { color ->
                ColorCount(color = color, count = clothing.count { color.key in it.colorKeys })
            }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }

        OverviewUiState(
            loading = false,
            wardrobeCount = wardrobeCount,
            clothingCount = clothing.size,
            outfitCount = outfitCount,
            categorySlices = slices,
            colorCounts = colorCounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())
}

/** 概览页 UI 状态 */
data class OverviewUiState(
    val loading: Boolean = true,
    val wardrobeCount: Int = 0,
    val clothingCount: Int = 0,
    val outfitCount: Int = 0,
    val categorySlices: List<CategorySlice> = emptyList(),
    val colorCounts: List<ColorCount> = emptyList(),
)

/** 类别分布扇区（大类聚合） */
data class CategorySlice(
    val topKey: String,
    val topName: String,
    val count: Int,
)

/** 颜色分布段 */
data class ColorCount(
    val color: ClothingColor,
    val count: Int,
)
