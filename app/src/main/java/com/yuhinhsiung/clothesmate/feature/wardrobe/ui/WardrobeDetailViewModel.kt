package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeItemFilter
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 衣柜详情页 ViewModel —— 开发计划 M1-09 / 开发文档 §2.1.2 W-04/W-09、UI 文档 §7.2。
 *
 * 衣物 3 列网格 + 状态筛选 chips（全部/正常/已收起/待完善）。
 */
@HiltViewModel
class WardrobeDetailViewModel @Inject constructor(
    private val wardrobeRepository: WardrobeRepository,
    private val clothingRepository: ClothingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 详情页 FAB「+ 录衣服」需要知道当前衣柜，作为录入确认页的衣柜预选（M1-14） */
    val wardrobeId: Long = savedStateHandle["wardrobeId"] ?: 0L

    private val selectedFilter = kotlinx.coroutines.flow.MutableStateFlow(WardrobeItemFilter.All)

    val uiState: StateFlow<WardrobeDetailUiState> = combine(
        wardrobeRepository.observeWardrobes(),
        clothingRepository.observeItemsInWardrobe(wardrobeId),
        selectedFilter,
    ) { wardrobes, items, filter ->
        val wardrobe = wardrobes.firstOrNull { it.id == wardrobeId }
        WardrobeDetailUiState(
            wardrobeId = wardrobeId,
            wardrobeName = wardrobe?.name.orEmpty(),
            wardrobeNote = wardrobe?.note,
            filter = filter,
            items = items.filter { filter.matches(it) },
            counts = WardrobeCounts(
                total = items.size,
                normal = items.count { !it.isStored },
                stored = items.count { it.isStored },
                incomplete = items.count { it.isIncomplete },
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WardrobeDetailUiState(),
    )

    fun onFilterChange(filter: WardrobeItemFilter) {
        selectedFilter.value = filter
    }

    fun toggleStored(itemId: Long) {
        viewModelScope.launch { clothingRepository.toggleStored(itemId) }
    }

    private fun WardrobeItemFilter.matches(item: ClothingItem): Boolean = when (this) {
        WardrobeItemFilter.All -> true
        WardrobeItemFilter.Normal -> !item.isStored
        WardrobeItemFilter.Stored -> item.isStored
        // 待完善判定：category 为空（开发文档 §2.2.4）
        WardrobeItemFilter.Incomplete -> item.isIncomplete
    }
}

/** 衣柜详情页 UI 状态 */
data class WardrobeDetailUiState(
    val wardrobeId: Long = 0L,
    val wardrobeName: String = "",
    val wardrobeNote: String? = null,
    val filter: WardrobeItemFilter = WardrobeItemFilter.All,
    val items: List<ClothingItem> = emptyList(),
    val counts: WardrobeCounts = WardrobeCounts(),
)

data class WardrobeCounts(
    val total: Int = 0,
    val normal: Int = 0,
    val stored: Int = 0,
    val incomplete: Int = 0,
)
