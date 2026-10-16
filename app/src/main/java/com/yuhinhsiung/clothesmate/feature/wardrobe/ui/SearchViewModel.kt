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

        // 风格筛选选项 = 现有衣物风格标签并集
        val styleOptions = searched.flatMap { it.styleTags }.distinct().sorted()

        // 结构化筛选在内存做（个人量级；类别筛选按大类展开成小类集合）
        val categoryKeys: Set<String>? = filters.category?.let { topKey ->
            ClothingCategory.entries.filter { it.topKey == topKey }.map { it.key }.toSet()
        }
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

    private fun outfitMatches(outfit: Outfit, token: String): Boolean =
        (outfit.name?.contains(token) == true) ||
            fallbackDisplayName(outfit.createdAt).contains(token) ||
            outfit.styleTags.any { it.contains(token) } ||
            outfit.sceneTags.any { it.contains(token) } ||
            outfit.seasonTags.any { key ->
                key.contains(token) || Season.fromKey(key)?.displayName?.contains(token) == true
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
    /** 类别筛选选项（大类词，与衣物选择器同口径） */
    val categoryOptions: List<Pair<String, String>>
        get() = listOf("top" to "上衣", "outer" to "外套", "bottom" to "下装", "dress" to "连衣裙")

    val colorOptions: List<ClothingColor>
        get() = ClothingColor.entries.toList()

    val hasAnyFilter: Boolean
        get() = categoryFilter != null || colorFilter != null || styleFilter != null
}
