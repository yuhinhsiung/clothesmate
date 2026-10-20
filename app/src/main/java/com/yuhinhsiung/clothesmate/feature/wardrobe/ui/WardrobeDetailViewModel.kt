package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeItemFilter
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val seasonMode = MutableStateFlow(SeasonModeState())
    private val _events = MutableSharedFlow<WardrobeDetailEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    private var lastSeasonUndo: SeasonUndo? = null

    val uiState: StateFlow<WardrobeDetailUiState> = combine(
        wardrobeRepository.observeWardrobes(),
        clothingRepository.observeItemsInWardrobe(wardrobeId),
        selectedFilter,
        seasonMode,
    ) { wardrobes, allItems, filter, season ->
        val wardrobe = wardrobes.firstOrNull { it.id == wardrobeId }
        val matched = allItems.filter { item -> season.matches(item) }
        WardrobeDetailUiState(
            loading = false,
            wardrobeId = wardrobeId,
            wardrobeName = wardrobe?.name.orEmpty(),
            wardrobeNote = wardrobe?.note,
            filter = filter,
            items = allItems.filter { filter.matches(it) },
            allItems = allItems,
            counts = WardrobeCounts(
                total = allItems.size,
                normal = allItems.count { !it.isStored },
                stored = allItems.count { it.isStored },
                incomplete = allItems.count { it.isIncomplete },
            ),
            seasonModeOpen = season.open,
            selectedSeasons = season.selectedSeasons,
            seasonItems = matched,
            seasonSelectedIds = season.selectedItems,
            seasonNormalCount = matched.count { !it.isStored },
            seasonStoredCount = matched.count { it.isStored },
            seasonSelectedNormalCount = matched.count { it.id in season.selectedItems && !it.isStored },
            seasonSelectedStoredCount = matched.count { it.id in season.selectedItems && it.isStored },
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

    fun openSeasonMode() {
        seasonMode.value = seasonMode.value.copy(open = true)
    }

    fun dismissSeasonMode() {
        seasonMode.value = seasonMode.value.copy(open = false, selectedItems = emptySet())
    }

    fun toggleSeason(key: String) {
        val current = seasonMode.value.selectedSeasons
        val next = if (key in current) current - key else current + key
        val matchedIds = uiState.value.allItems
            .filter { item -> next.containsSeason(item) }
            .map { it.id }
            .toSet()
        seasonMode.value = seasonMode.value.copy(
            selectedSeasons = next,
            // 季节切换后只保留仍匹配的选择，避免隐藏项被误操作。
            selectedItems = seasonMode.value.selectedItems.intersect(matchedIds),
        )
    }

    fun toggleSeasonItem(itemId: Long) {
        val current = seasonMode.value.selectedItems
        seasonMode.value = seasonMode.value.copy(
            selectedItems = if (itemId in current) current - itemId else current + itemId,
        )
    }

    fun setAllSeasonItemsSelected(selected: Boolean) {
        val ids = if (selected) {
            uiState.value.seasonItems.map { it.id }.toSet()
        } else {
            emptySet()
        }
        seasonMode.value = seasonMode.value.copy(selectedItems = ids)
    }

    /** 对勾选的项目执行批量收起/恢复；由 UI 二次确认后调用。 */
    fun applySelectedSeasonStored(stored: Boolean) {
        val state = uiState.value
        val ids = state.seasonItems
            .filter { it.id in state.seasonSelectedIds }
            .filter { if (stored) !it.isStored else it.isStored }
            .map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            clothingRepository.setStored(ids, stored)
            lastSeasonUndo = SeasonUndo(ids = ids, stored = stored)
            seasonMode.value = seasonMode.value.copy(selectedItems = emptySet())
            _events.emit(WardrobeDetailEvent.SeasonChanged(ids.size, stored))
        }
    }

    /** M4-04：按当前季节选择批量收起 / 恢复；由 UI 二次确认后调用。 */
    fun applySeasonStored(stored: Boolean) {
        val state = uiState.value
        val ids = state.allItems
            .filter { state.selectedSeasons.containsSeason(it) }
            .filter { if (stored) !it.isStored else it.isStored }
            .map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            clothingRepository.setStored(ids, stored)
            lastSeasonUndo = SeasonUndo(ids = ids, stored = stored)
            _events.emit(WardrobeDetailEvent.SeasonChanged(ids.size, stored))
        }
    }

    fun undoSeasonChange() {
        val undo = lastSeasonUndo ?: return
        lastSeasonUndo = null
        viewModelScope.launch {
            clothingRepository.setStored(undo.ids, !undo.stored)
            _events.emit(WardrobeDetailEvent.SeasonUndone(undo.ids.size))
        }
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
    /** review B-22：首帧数据未到前不误显「这个衣柜还是空的」（M1-23 骨架体系） */
    val loading: Boolean = true,
    val wardrobeId: Long = 0L,
    val wardrobeName: String = "",
    val wardrobeNote: String? = null,
    val filter: WardrobeItemFilter = WardrobeItemFilter.All,
    val items: List<ClothingItem> = emptyList(),
    val allItems: List<ClothingItem> = emptyList(),
    val counts: WardrobeCounts = WardrobeCounts(),
    val seasonModeOpen: Boolean = false,
    val selectedSeasons: Set<String> = emptySet(),
    val seasonItems: List<ClothingItem> = emptyList(),
    val seasonSelectedIds: Set<Long> = emptySet(),
    val seasonNormalCount: Int = 0,
    val seasonStoredCount: Int = 0,
    val seasonSelectedNormalCount: Int = 0,
    val seasonSelectedStoredCount: Int = 0,
)

private data class SeasonModeState(
    val open: Boolean = false,
    val selectedSeasons: Set<String> = emptySet(),
    val selectedItems: Set<Long> = emptySet(),
) {
    fun matches(item: ClothingItem): Boolean = selectedSeasons.containsSeason(item)
}

private data class SeasonUndo(val ids: List<Long>, val stored: Boolean)

sealed interface WardrobeDetailEvent {
    data class SeasonChanged(val count: Int, val stored: Boolean) : WardrobeDetailEvent
    data class SeasonUndone(val count: Int) : WardrobeDetailEvent
}

data class WardrobeCounts(
    val total: Int = 0,
    val normal: Int = 0,
    val stored: Int = 0,
    val incomplete: Int = 0,
)

/** 季节匹配：兼容表单写入的中文 displayName 与旧数据的英文 key。 */
private fun Set<String>.containsSeason(item: ClothingItem): Boolean =
    item.seasonTags.any { tag ->
        any { selected ->
            selected == tag ||
                Season.fromKey(selected)?.displayName == tag ||
                Season.fromKey(tag)?.key == selected
        }
    }
