package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 衣物详情页 ViewModel —— 开发计划 M1-11 / 开发文档 §2.1.2 W-05、UI 文档 §7.2。
 *
 * M1 不含「一衣多穿」区（M4-03 才做），此处只呈现基本信息与底部三个操作。
 */
@HiltViewModel
class ClothingDetailViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    private val wardrobeRepository: WardrobeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    /** 撤销软删除后触发重查： restore 会清掉 deletedAt，observeItem 应重新吐出 */
    private val reloadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ClothingDetailUiState> = combine(
        clothingRepository.observeItem(itemId),
        wardrobeRepository.observeWardrobes(),
        reloadTrigger,
    ) { item, wardrobes, _ ->
        if (item == null) {
            ClothingDetailUiState(notFound = true)
        } else {
            ClothingDetailUiState(
                item = item,
                wardrobeName = wardrobes.firstOrNull { it.id == item.wardrobeId }?.name,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ClothingDetailUiState(),
    )

    /** 一键切换 收起 / 恢复正常（W-09） */
    fun toggleStored() {
        val item = uiState.value.item ?: return
        viewModelScope.launch { clothingRepository.toggleStored(item.id) }
    }

    /** 软删除（W-13）。删除后衣物从 Flow 消失，UI 据此关闭页面并给撤销入口。 */
    fun softDelete() {
        val item = uiState.value.item ?: return
        viewModelScope.launch { clothingRepository.softDelete(item.id) }
    }

    /** 撤销软删除（W-13 Snackbar「撤销」） */
    fun undoDelete() {
        viewModelScope.launch { clothingRepository.restore(itemId) }
    }
}

/** 衣物详情页 UI 状态 */
data class ClothingDetailUiState(
    val item: ClothingItem? = null,
    val wardrobeName: String? = null,
    val notFound: Boolean = false,
) {
    val isStored: Boolean
        get() = item?.isStored == true

    /** 穿着次数文案：0 次也要优雅降级 */
    val wearLabel: String
        get() = when (item?.wearCount ?: 0) {
            0 -> "还没穿过"
            1 -> "穿过 1 次"
            else -> "穿过 ${item?.wearCount} 次"
        }

    /** 所属衣柜文案 */
    val wardrobeLabel: String
        get() = wardrobeName ?: "未知衣柜"

    /** 类别展示名 */
    val categoryLabel: String
        get() = item?.categoryKey
            ?.let { ClothingCategory.fromKey(it)?.displayName }
            ?: "待完善"

    /** 颜色展示名列表（key + 枚举，供 UI 画色点） */
    val colorLabels: List<Pair<String, ClothingColor?>>
        get() = item?.colorKeys?.map { key -> key to ClothingColor.fromKey(key) } ?: emptyList()

    /** 季节展示名列表 */
    val seasonLabels: List<String>
        get() = item?.seasonTags?.mapNotNull { Season.fromKey(it)?.displayName } ?: emptyList()
}
