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

    /** 可选标签全集：取自**当前数据源**（全部或分类内）穿搭的并集——review B-11：
     *  此前恒取全部穿搭，分类内页会出现与本分类无关的筛选 chips 且空态误判 */
    val availableTags: StateFlow<AvailableTags> = sourceOutfits(outfitRepository)
        .map { outfits ->
            AvailableTags(
                styles = outfits.flatMap { it.styleTags }.distinct().sorted(),
                scenes = outfits.flatMap { it.sceneTags }.distinct().sorted(),
                seasons = outfits.flatMap { it.seasonTags }.distinct().sorted(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvailableTags())

    /** 是否分类内入口（空态三分支判定用，B-11） */
    val isCategoryScoped: Boolean
        get() = categoryId != null

    val outfits: StateFlow<List<com.yuhinhsiung.clothesmate.core.model.Outfit>> = combine(
        sourceOutfits(outfitRepository),
        styleFilter,
        sceneFilter,
        seasonFilter,
    ) { list, styles, scenes, seasons ->
        list.filter { outfit ->
            (styles.isEmpty() || outfit.styleTags.any { it in styles }) &&
                (scenes.isEmpty() || outfit.sceneTags.any { it in scenes }) &&
                (seasons.isEmpty() || outfit.seasonTags.any { it in seasons })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun sourceOutfits(repository: OutfitRepository) =
        if (categoryId == null) {
            repository.observeOutfits()
        } else {
            repository.observeOutfitsInCategory(categoryId)
        }

    fun toggleStyle(tag: String) = styleFilter.toggle(tag)
    fun toggleScene(tag: String) = sceneFilter.toggle(tag)
    fun toggleSeason(tag: String) = seasonFilter.toggle(tag)

    fun styleSelection(): StateFlow<Set<String>> = styleFilter.asStateFlow()
    fun sceneSelection(): StateFlow<Set<String>> = sceneFilter.asStateFlow()
    fun seasonSelection(): StateFlow<Set<String>> = seasonFilter.asStateFlow()

    private fun MutableStateFlow<Set<String>>.toggle(tag: String) {
        value = if (tag in value) value - tag else value + tag
    }
}

/** 瀑布流筛选条可选标签（现有穿搭的并集） */
data class AvailableTags(
    val styles: List<String> = emptyList(),
    val scenes: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = styles.isEmpty() && scenes.isEmpty() && seasons.isEmpty()
}
