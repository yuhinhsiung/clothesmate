package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeDeleteDestiny
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 删除衣柜去向弹层 —— 开发计划 M1-07 / 开发文档 §2.1.2 W-12、UI 文档 §7.2、mockup-20。
 *
 * W-12：删除弹层中选 ①转移到其他衣柜（默认）②一并删除其中衣物（error 色警示 + 二次确认）。
 * 「仅剩一个衣柜时选项①自动选定该柜」——由 [load] 计算默认选中项。
 */
@HiltViewModel
class WardrobeDeleteViewModel @Inject constructor(
    private val wardrobeRepository: WardrobeRepository,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WardrobeDeleteUiState())
    val uiState: StateFlow<WardrobeDeleteUiState> = _uiState.asStateFlow()

    /**
     * 载入待删衣柜信息，并算出可转移的目标列表。
     *
     * 只有一件衣柜时目标列表为空，UI 据此把选项①置灰并提示先新建
     * （UI 文档 §7.2 的「自动选定该柜」只适用于「目标是另一件已存在的衣柜」——
     *  转移到自身是自毁操作，见 M1-04 的防御）。
     */
    fun load(wardrobeId: Long) {
        viewModelScope.launch {
            val wardrobe = wardrobeRepository.getWardrobe(wardrobeId) ?: return@launch
            val others = wardrobeRepository.getAllWardrobes().filter { it.id != wardrobeId }
            _uiState.update {
                it.copy(
                    wardrobe = wardrobe,
                    itemCount = wardrobe.clothingCount,
                    targets = others,
                    // 默认选中①；若只有一件衣柜则无目标可转，退到②
                    selectedTargetId = others.firstOrNull()?.id,
                )
            }
        }
    }

    fun onDestinyChange(transfer: Boolean) {
        _uiState.update { it.copy(transferSelected = transfer) }
    }

    fun onTargetChange(wardrobeId: Long) {
        _uiState.update { it.copy(selectedTargetId = wardrobeId) }
    }

    /**
     * 执行删除。
     *
     * 衣物图片文件在衣柜行删除成功后统一清理（[ImageStorage.deleteClothingImages]），
     * 软删/转移都不会走到这里——只有「一并删除」与转移都不该动文件，
     * 故只在删除 wardrobe 时按"衣物是否还在库"判断：
     * - 转移 → 衣物还在（换了衣柜），不删图
     * - 一并删除 → 衣物物理删除，删图
     */
    fun confirm(onDone: (DeleteResult) -> Unit) {
        val state = _uiState.value
        val wardrobe = state.wardrobe ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true) }
            val transfer = state.transferSelected
            val targetId = state.selectedTargetId
            val destiny = if (transfer && targetId != null) {
                WardrobeDeleteDestiny.TransferTo(targetId)
            } else {
                WardrobeDeleteDestiny.DeleteAll
            }

            runCatching {
                wardrobeRepository.deleteWithDestiny(
                    wardrobeId = wardrobe.id,
                    destiny = destiny,
                ) { deletedIds ->
                    // 只有物理删除的衣物才清图；转移的衣物已被 moveToWardrobe 保留
                    if (destiny is WardrobeDeleteDestiny.DeleteAll) {
                        deletedIds.forEach { imageStorage.deleteClothingImages(it) }
                    }
                }
            }
                .onSuccess { count ->
                    _uiState.update { it.copy(deleting = false) }
                    val targetName = if (transfer) {
                        state.targets.firstOrNull { it.id == targetId }?.name
                    } else {
                        null
                    }
                    onDone(DeleteResult(count = count, transferredTo = targetName))
                }
                .onFailure {
                    _uiState.update { it.copy(deleting = false, error = "删除失败，请重试") }
                }
        }
    }
}

/** 删除结果：件数 + （转移时）目标衣柜名，供 Snackbar 文案使用 */
data class DeleteResult(
    val count: Int,
    val transferredTo: String?,
)

/** 删除弹层 UI 状态 */
data class WardrobeDeleteUiState(
    val wardrobe: Wardrobe? = null,
    val itemCount: Int = 0,
    val targets: List<Wardrobe> = emptyList(),
    val selectedTargetId: Long? = null,
    /** true = 选中①转移；false = 选中②一并删除 */
    val transferSelected: Boolean = true,
    val deleting: Boolean = false,
    val error: String? = null,
) {
    /** 只有一件衣柜时无转移目标，选项①不可用 */
    val canTransfer: Boolean
        get() = targets.isNotEmpty()

    val canConfirm: Boolean
        get() = wardrobe != null && !deleting &&
            (if (transferSelected) selectedTargetId != null else true)
}
