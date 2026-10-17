package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.core.model.fallbackDisplayName
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 全局搜索 ViewModel —— M1-16 / W-07。
 *
 * - 衣物：[ClothingRepository.search]（分词 AND、跨衣柜、枚举中文别名翻译），再叠
 *   结构化筛选（类别按大类展开 / 颜色 / 风格）；
 * - 穿搭：[OutfitDao.observeAll]（core DAO，不引 outfit feature 的 Repository 实现）
 *   内存过滤——命中名称（含日期兜底名）/ 风格 / 场景 / 季节（含中文季节名）。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    outfitDao: OutfitDao,
    wardrobeDao: WardrobeDao,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<String?>(null)
    private val colorFilter = MutableStateFlow<String?>(null)
    private val styleFilter = MutableStateFlow<String?>(null)

    /**
     * 分词 + 枚举别名翻译在 [ClothingRepository.search] 内完成（W-07「红 上衣」这类
     * 口语命中），query 变化经 flatMapLatest 切换上游；结构化筛选（大类展开 / 颜色 /
     * 风格）在内存叠加。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val searchedItems = kotlinx.coroutines.flow.combine(
        query,
        categoryFilter,
        colorFilter,
        styleFilter,
    ) { q, c, col, s -> SearchFilters(q, c, col, s) }
        .flatMapLatest { filters -> clothingRepository.search(filters.query) }

    val uiState: StateFlow<SearchUiState> = combine(
        combine(query, categoryFilter, colorFilter, styleFilter) { q, c, col, s ->
            SearchFilters(q, c, col, s)
        },
        searchedItems,
        outfitDao.observeAll(),
        wardrobeDao.observeAll(),
    ) { filters, searched, outfits, wardrobes ->
        val tokens = filters.query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val wardrobeNames = wardrobes.associate { it.id to it.name }

        // review B-07/B-28：类别筛选词收敛到 ClothingCategory.filterChips 单一词源
        // （含「鞋包配饰」合并词）；类别行不再被风格条件误门控（那是 B-07 的复制粘贴错位）
        val categoryKeys = ClothingCategory.filterCategoryKeys(filters.category)

        // review B-08：风格筛选「幽灵生效」——已选值不在选项集（换关键词后选项收缩）时
        // 仍保留在选项列表里，用户可以原地取消，而不是被看不见的条件过滤到空结果
        val allStyleTags = searched.flatMap { it.styleTags }.distinct()
        val styleOptions = (allStyleTags + listOfNotNull(
            filters.style?.takeIf { selected -> selected !in allStyleTags },
        )).sorted()
        val items = searched
            .asSequence()
            .filter { item -> categoryKeys == null || item.categoryKey in categoryKeys }
            .filter { item -> filters.color == null || filters.color in item.colorKeys }
            .filter { item -> filters.style == null || filters.style in item.styleTags }
            .toList()

        // 穿搭：命中名称（含日期兜底名）/ 风格 / 场景 / 季节（中文），token 间 AND
        val filteredOutfits = outfits
            .asSequence()
            .map { it.toDomain() }
            .filter { filters.style == null || filters.style in it.styleTags }
            .filter { outfit ->
                tokens.isEmpty() || tokens.all { token ->
                    outfitMatches(outfit, token)
                }
            }
            .toList()

        SearchUiState(
            query = filters.query,
            tokens = tokens,
            items = items,
            outfits = filteredOutfits,
            wardrobeNames = wardrobeNames,
            categoryFilter = filters.category,
            colorFilter = filters.color,
            styleFilter = filters.style,
            styleOptions = styleOptions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** review B-33：大小写口径与衣物 LIKE / HighlightedText 对齐（统一不敏感） */
    private fun String.containsFold(other: String): Boolean =
        lowercase().contains(other.lowercase())

    private fun outfitMatches(outfit: Outfit, token: String): Boolean =
        (outfit.name?.containsFold(token) == true) ||
            fallbackDisplayName(outfit.createdAt).containsFold(token) ||
            outfit.styleTags.any { it.containsFold(token) } ||
            outfit.sceneTags.any { it.containsFold(token) } ||
            outfit.seasonTags.any { key ->
                key.containsFold(token) || Season.fromKey(key)?.displayName?.containsFold(token) == true
            }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategoryFilter(key: String?) {
        categoryFilter.value = key
    }

    fun onColorFilter(key: String?) {
        colorFilter.value = key
    }

    fun onStyleFilter(tag: String?) {
        styleFilter.value = tag
    }
}

private data class SearchFilters(
    val query: String,
    val category: String?,
    val color: String?,
    val style: String?,
)

/** 搜索结果页 UI 状态 */
data class SearchUiState(
    val query: String = "",
    /** 分词结果（高亮用） */
    val tokens: List<String> = emptyList(),
    val items: List<ClothingItem> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val wardrobeNames: Map<Long, String> = emptyMap(),
    val categoryFilter: String? = null,
    val colorFilter: String? = null,
    val styleFilter: String? = null,
    val styleOptions: List<String> = emptyList(),
) {
    /** 类别筛选选项（review B-28：与衣物选择器同一词源 ClothingCategory.filterChips，含「鞋包配饰」） */
    val categoryOptions: List<Pair<String?, String>>
        get() = ClothingCategory.filterChips

    val colorOptions: List<ClothingColor>
        get() = ClothingColor.entries.toList()

    val hasAnyFilter: Boolean
        get() = categoryFilter != null || colorFilter != null || styleFilter != null
}
