package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.core.model.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 衣物选择器 ViewModel —— M1-18。
 *
 * 数据源走 core DAO（ClothingItemDao / WardrobeDao），不引用 wardrobe feature 的
 * Repository 实现（模块边界，见 OutfitRepository 同注）。收起衣物默认隐藏（W-09），
 * 「已收起 n」chip 显式展开。选择保持点击顺序（序号圆点与确认页横滑列一致）。
 */
@HiltViewModel
class ClothingPickerViewModel @Inject constructor(
    clothingDao: ClothingItemDao,
    wardrobeDao: WardrobeDao,
    private val navResultBus: com.yuhinhsiung.clothesmate.ui.navigation.NavResultBus,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val wardrobeFilter = MutableStateFlow<Long?>(null)
    private val categoryFilter = MutableStateFlow<String?>(null)
    private val includeStored = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow(emptyList<Long>())

    init {
        // review B-19：进入即预填当前已选（含顺序）——表单经 NavResultBus 发布种子
        // （SavedStateHandle 回传失效，见 review/2026-10-17_1015_review.md B-01）。
        viewModelScope.launch {
            navResultBus.pickerSeed().collect { seed ->
                if (seed != null) {
                    selectedIds.value = seed
                    navResultBus.consumePickerSeed()
                }
            }
        }
    }

    /**
     * 「完成」：经 NavResultBus 发布最终选择（完整列表、含顺序），随后由 Route 收页。
     * 发布必须先于 pop——表单 VM 在返回栈下始终存活、即时收集。
     */
    fun confirmSelection() {
        navResultBus.publishPickedItemIds(selectedIds.value)
    }

    val uiState: StateFlow<ClothingPickerUiState> = combine(
        clothingDao.observeAll(),
        wardrobeDao.observeAll(),
        query,
        wardrobeFilter,
        combine(categoryFilter, includeStored, selectedIds) { c, s, ids -> Triple(c, s, ids) },
    ) { items, wardrobes, q, w, (cat, stored, ids) ->
        val domain = items.map { it.toDomain() }
        val tokens = q.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        // review B-28：类别筛选词收敛到 ClothingCategory.filterCategoryKeys 单一词源
        // （「鞋包配饰」合并词此前在选择器与搜索页各硬编码一份且不一致）
        val categoryKeys = ClothingCategory.filterCategoryKeys(cat)
        ClothingPickerUiState(
            items = domain.filter { item ->
                stored || !item.isStored
            }.filter { item ->
                w == null || item.wardrobeId == w
            }.filter { item ->
                categoryKeys == null || item.categoryKey in categoryKeys
            }.filter { item ->
                tokens.isEmpty() || tokens.all { token ->
                    item.code.contains(token) ||
                        item.displayName.contains(token) ||
                        item.styleTags.any { it.contains(token) }
                }
            }.sortedBy { it.code.toLongOrNull() ?: Long.MAX_VALUE },
            wardrobes = wardrobes.map { it.toDomain() },
            query = q,
            wardrobeFilter = w,
            categoryFilter = cat,
            includeStored = stored,
            storedCount = domain.count { it.isStored },
            selectedIds = ids,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClothingPickerUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onWardrobeFilter(wardrobeId: Long?) {
        wardrobeFilter.value = wardrobeId
    }

    fun onCategoryFilter(categoryKey: String?) {
        categoryFilter.value = categoryKey
    }

    fun toggleIncludeStored() {
        includeStored.value = !includeStored.value
    }

    /** 多选：保持点击顺序（重选已选项则移除） */
    fun toggleSelection(itemId: Long) {
        selectedIds.value = if (itemId in selectedIds.value) {
            selectedIds.value - itemId
        } else {
            selectedIds.value + itemId
        }
    }
}

/** 衣物选择器 UI 状态 */
data class ClothingPickerUiState(
    val items: List<ClothingItem> = emptyList(),
    val wardrobes: List<Wardrobe> = emptyList(),
    val query: String = "",
    val wardrobeFilter: Long? = null,
    val categoryFilter: String? = null,
    val includeStored: Boolean = false,
    val storedCount: Int = 0,
    /** 已选 id，按选择顺序（序号圆点） */
    val selectedIds: List<Long> = emptyList(),
)
