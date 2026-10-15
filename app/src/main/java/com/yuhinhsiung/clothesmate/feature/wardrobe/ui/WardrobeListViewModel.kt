package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 衣柜列表页 ViewModel —— 开发计划 M1-05 / 开发文档 §2.1.2 W-03/W-08。
 *
 * UI 状态直接由 Repository 的 Flow 派生：Room 是唯一事实来源，增删衣物后
 * 件数自动刷新，不需要手动 invalidate（§4.1.2）。
 */
@HiltViewModel
class WardrobeListViewModel @Inject constructor(
    private val wardrobeRepository: WardrobeRepository,
) : ViewModel() {

    val uiState: StateFlow<WardrobeListUiState> = wardrobeRepository.observeWardrobes()
        .map { list ->
            if (list.isEmpty()) {
                WardrobeListUiState.Empty
            } else {
                WardrobeListUiState.Content(list)
            }
        }
        .stateIn(
            scope = viewModelScope,
            // 5 秒停止期：旋屏 / 切后台回来不重放查询，又不至于长期持有 Flow
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WardrobeListUiState.Loading,
        )

    /**
     * 拖拽排序持久化（W-08）。
     *
     * @param orderedIds 拖拽后的衣柜 id 顺序（UI 已在本地重排好，这里只落库）
     */
    fun persistOrder(orderedIds: List<Long>) {
        viewModelScope.launch {
            wardrobeRepository.persistOrder(orderedIds)
        }
    }
}

/** 衣柜列表页 UI 状态。Loading 时期显示骨架屏（UI 文档 §6.9）。 */
sealed interface WardrobeListUiState {
    data object Loading : WardrobeListUiState
    data object Empty : WardrobeListUiState
    data class Content(val wardrobes: List<Wardrobe>) : WardrobeListUiState
}
