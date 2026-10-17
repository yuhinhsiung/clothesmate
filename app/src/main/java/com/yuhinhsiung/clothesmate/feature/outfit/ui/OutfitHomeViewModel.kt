package com.yuhinhsiung.clothesmate.feature.outfit.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.core.model.OutfitCategory
import com.yuhinhsiung.clothesmate.feature.outfit.data.OutfitRepository
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitWaterfall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 穿搭分类首页 ViewModel —— M1-17。
 *
 * 分类卡带实时套数与封面（分类内最新一套有照片穿搭的封面，O-02），Repository 层组装。
 */
@HiltViewModel
class OutfitHomeViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
) : ViewModel() {

    private val categoriesFlow = outfitRepository.observeCategories()
    private val outfitsFlow = outfitRepository.observeOutfits()

    val categories: StateFlow<List<OutfitCategory>> = categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部穿搭套数（顶部大卡） */
    val totalCount: StateFlow<Int> = outfitsFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** review B-22：以「原始流首个发射」判定加载完成——StateFlow 的 initialValue
     *  无法区分「加载中」与「真空」，须用 dataIn 前的源流。 */
    val loading: StateFlow<Boolean> = combine(categoriesFlow, outfitsFlow) { _, _ -> false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun renameCategory(categoryId: Long, name: String) {
        viewModelScope.launch { outfitRepository.renameCategory(categoryId, name) }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { outfitRepository.deleteCategory(categoryId) }
    }
}

/**
 * 瀑布流 ViewModel —— M1-19。
 *
 * 「全部穿搭」（categoryId = null）与「分类内穿搭」同一组件同一数据源，仅来源 flow 不同
 * （开发计划 M1-19 备注：勿造两套页面）。顶部标签筛选条对风格 / 场景 / 季节三组做交集过滤。
 */
@HiltViewModel
class OutfitWaterfallViewModel @Inject constructor(
    outfitRepository: OutfitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** null = 全部穿搭（O-04）；非空 = 分类内（O-03） */
    val categoryId: Long? = savedStateHandle.toRoute<OutfitWaterfall>().categoryId

    private val styleFilter = MutableStateFlow(emptySet<String>())
    private val sceneFilter = MutableStateFlow(emptySet<String>())
    private val seasonFilter = MutableStateFlow(emptySet<String>())

    /** 分类名（页面标题用）；全部穿搭时为 null */
    val categoryName: StateFlow<String?> = outfitRepository.observeCategories()
        .map { categories -> categories.firstOrNull { it.id == categoryId }?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 是否分类内入口（空态分支判定用，B-11） */
    val isCategoryScoped: Boolean
        get() = categoryId != null

    /**
     * **筛选前**的源列表；`null` = 首帧还没到。
     *
     * review R-12：原先只有一个「已过滤」的列表，空态拿它去区分「分类里没有穿搭」
     * 和「筛选没命中」——「本分类有穿搭但筛选组合没命中」同样落进「这个分类还没有穿搭」
     * 并给出创建按钮。源列表和过滤结果必须分开暴露。
     */
    private val sourceOutfits: StateFlow<List<com.yuhinhsiung.clothesmate.core.model.Outfit>?> =
        sourceFlow(outfitRepository)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<OutfitWaterfallUiState> = combine(
        sourceOutfits,
        styleFilter,
        sceneFilter,
        seasonFilter,
    ) { source, styles, scenes, seasons ->
        if (source == null) return@combine OutfitWaterfallUiState(loading = true)
        OutfitWaterfallUiState(
            loading = false,
            sourceCount = source.size,
            outfits = source.filter { outfit ->
                (styles.isEmpty() || outfit.styleTags.any { it in styles }) &&
                    (scenes.isEmpty() || outfit.sceneTags.any { it in scenes }) &&
                    (seasons.isEmpty() || outfit.seasonTags.any { it in seasons })
            },
            // 可选标签取自**当前数据源**（全部或分类内）穿搭的并集——review B-11：
            // 此前恒取全部穿搭，分类内页会出现与本分类无关的筛选 chips
            availableTags = AvailableTags(
                styles = source.flatMap { it.styleTags }.distinct().sorted(),
                scenes = source.flatMap { it.sceneTags }.distinct().sorted(),
                seasons = source.flatMap { it.seasonTags }.distinct().sorted(),
            ),
            hasActiveFilter = styles.isNotEmpty() || scenes.isNotEmpty() || seasons.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutfitWaterfallUiState())

    private fun sourceFlow(repository: OutfitRepository) =
        if (categoryId == null) {
            repository.observeOutfits()
        } else {
            repository.observeOutfitsInCategory(categoryId)
        }

    fun toggleStyle(tag: String) = styleFilter.toggle(tag)
    fun toggleScene(tag: String) = sceneFilter.toggle(tag)
    fun toggleSeason(tag: String) = seasonFilter.toggle(tag)

    /** 一键清空三组筛选（「筛选无结果」空态的主按钮，review R-12） */
    fun clearFilters() {
        styleFilter.value = emptySet()
        sceneFilter.value = emptySet()
        seasonFilter.value = emptySet()
    }

    fun styleSelection(): StateFlow<Set<String>> = styleFilter.asStateFlow()
    fun sceneSelection(): StateFlow<Set<String>> = sceneFilter.asStateFlow()
    fun seasonSelection(): StateFlow<Set<String>> = seasonFilter.asStateFlow()

    private fun MutableStateFlow<Set<String>>.toggle(tag: String) {
        value = if (tag in value) value - tag else value + tag
    }
}

/**
 * 瀑布流页状态。
 *
 * [sourceCount] 与 [outfits] 的区别是本页空态判定的全部依据（review R-12）：
 * 源为空才是「这里还没有穿搭」，源非空而过滤结果为空是「筛选没命中」。
 */
data class OutfitWaterfallUiState(
    val loading: Boolean = true,
    val sourceCount: Int = 0,
    val outfits: List<com.yuhinhsiung.clothesmate.core.model.Outfit> = emptyList(),
    val availableTags: AvailableTags = AvailableTags(),
    val hasActiveFilter: Boolean = false,
)

/** 瀑布流筛选条可选标签（现有穿搭的并集） */
data class AvailableTags(
    val styles: List<String> = emptyList(),
    val scenes: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = styles.isEmpty() && scenes.isEmpty() && seasons.isEmpty()
}
