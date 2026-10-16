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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    /**
     * 编辑页（M1-12）保存成功的回执：编辑页写回本条目的 SavedStateHandle 后 pop，
     * 详情页据此弹「已保存」Snackbar（M1-12 验收「保存成功 Snackbar」）。
     */
    val savedSignal: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_EDIT_SAVED, false)

    fun consumeSavedSignal() {
        savedStateHandle[KEY_EDIT_SAVED] = false
    }

    // 撤销软删除后无需手动触发重查：observeItem 的 Room Flow 会在 restore
    // 清掉 deletedAt 后自然重发该行（review U-i：原先那个 reloadTrigger 是死代码）
    val uiState: StateFlow<ClothingDetailUiState> = combine(
        clothingRepository.observeItem(itemId),
        wardrobeRepository.observeWardrobes(),
    ) { item, wardrobes ->
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

    /**
     * 最近穿着日期（epoch millis），未穿过为 null（review C-19）。
     *
     * M1 无穿着记录（T-05 属 M3），故恒为 null、属性卡不显示日期；
     * M3 接上 wear_record 后这里自然有值。VM 只透出时间戳，
     * 格式化在 UI 层做（android.text.format.DateFormat 只能在创建它的线程用）。
     */
    val lastWornAt: Long?
        get() = item?.lastWornAt

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

/** 编辑页 → 详情页的保存回执 key（ClothingDetailViewModel.savedSignal） */
private const val KEY_EDIT_SAVED = "clothing_edit_saved"
