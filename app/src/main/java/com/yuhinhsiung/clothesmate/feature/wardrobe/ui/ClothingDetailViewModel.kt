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
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val navResultBus: com.yuhinhsiung.clothesmate.ui.navigation.NavResultBus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // review B-26：路由参数改走 toRoute（SavedStateHandle 字符串取参与 key 约定漂移）
    private val itemId: Long = savedStateHandle.toRoute<com.yuhinhsiung.clothesmate.ui.navigation.ClothingDetail>()
        .itemId

    /**
     * 编辑页（M1-12）保存成功的回执：表单保存后经 [NavResultBus] 发布（review B-04：
     * SavedStateHandle 回传在本工程失效，改显式总线），本 VM 收集后置位、
     * 详情页据此弹「已保存」Snackbar 并 [consumeSavedSignal]。
     */
    private val _savedSignal = MutableStateFlow(false)
    val savedSignal: StateFlow<Boolean> = _savedSignal.asStateFlow()

    init {
        viewModelScope.launch {
            navResultBus.clothingSaved().collect { saved ->
                if (saved) _savedSignal.value = true
            }
        }
    }

    fun consumeSavedSignal() {
        _savedSignal.value = false
        navResultBus.consumeClothingSaved()
    }

    // 撤销软删除后无需手动触发重查：observeItem 的 Room Flow 会在 restore
    // 清掉 deletedAt 后自然重发该行（review U-i：原先那个 reloadTrigger 是死代码）

    /**
     * 删除弹窗用的「被几套穿搭引用」（review R-10）。
     * 打开弹窗时按需查一次——引用关系会随别处增删穿搭变化，缓存到进页面时算不准。
     */
    private val outfitRefCount = MutableStateFlow(0)

    val uiState: StateFlow<ClothingDetailUiState> = combine(
        clothingRepository.observeItem(itemId),
        wardrobeRepository.observeWardrobes(),
        outfitRefCount,
    ) { item, wardrobes, refCount ->
        if (item == null) {
            ClothingDetailUiState(notFound = true)
        } else {
            ClothingDetailUiState(
                item = item,
                wardrobeName = wardrobes.firstOrNull { it.id == item.wardrobeId }?.name,
                outfitRefCount = refCount,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ClothingDetailUiState(),
    )

    /** 打开删除确认前刷新引用数（M1-13 / W-13 的「这些穿搭中将显示『已删除』」） */
    fun loadOutfitRefCount() {
        viewModelScope.launch {
            outfitRefCount.value = clothingRepository.countOutfitsForItem(itemId)
        }
    }

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
    /** 被几套现存穿搭引用（删除弹窗警示，review R-10） */
    val outfitRefCount: Int = 0,
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
