package com.yuhinhsiung.clothesmate.feature.overview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.dao.WearRecordDao
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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
    wearRecordDao: WearRecordDao,
) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> = combine(
        wardrobeDao.observeCount(),
        clothingDao.observeAll(),
        outfitDao.observeCount(),
        wearRecordDao.observeAll(),
    ) { wardrobeCount, clothingEntities, outfitCount, records ->
        OverviewAggregation.aggregate(
            clothing = clothingEntities.map { it.toDomain() },
            wearDates = records.map { it.date },
            wardrobeCount = wardrobeCount,
            outfitCount = outfitCount,
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
    val topWorn: List<RankedClothing> = emptyList(),
    val leastWorn: List<RankedClothing> = emptyList(),
    val heatmap: List<HeatmapDay> = emptyList(),
    val heatmapStart: LocalDate? = null,
    val seasonSlices: List<SeasonSlice> = emptyList(),
    /** 旧数据只有 wearCount、没有可还原日期的穿着记录。 */
    val legacyWearCount: Int = 0,
) {
    val seasonTotal: Int
        get() = seasonSlices.sumOf { it.count }
}

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
