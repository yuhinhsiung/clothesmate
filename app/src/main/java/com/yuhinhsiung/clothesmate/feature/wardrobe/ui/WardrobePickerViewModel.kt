package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.KEY_LAST_WARDROBE
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 选择衣柜弹层 —— 开发计划 M1-08 / 开发文档 §12.1 #10、UI 文档 §7.2、mockup-18。
 *
 * 默认选中优先级（§7.2）：**当前已选 > 上次使用（DataStore）> sortOrder 首个**。
 * 选中后写回 DataStore `lastWardrobeId`，下次录入时预选。
 */
@HiltViewModel
class WardrobePickerViewModel @Inject constructor(
    private val wardrobeRepository: WardrobeRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WardrobePickerUiState())
    val uiState: StateFlow<WardrobePickerUiState> = _uiState.asStateFlow()

    /** 订阅只看一次（弹层可能因重组多次触发 load） */
    private var loadJob: Job? = null

    /**
     * 载入并**持续订阅**衣柜列表。
     *
     * review R-04：原实现是一次性快照。弹层内「＋ 新建衣柜」成功后新柜已经落库，
     * 但快照里没有它——`confirm()` 在旧列表里找不到新 id 直接 return，既不选中也不回传。
     * 改成订阅 Room Flow 之后，新建的衣柜会自动出现在列表里。
     *
     * @param currentSelected 当前已选的衣柜 id（编辑场景传；新建传 null）
     */
    fun load(currentSelected: Long? = null) {
        if (loadJob != null) return
        loadJob = viewModelScope.launch {
            val lastUsed = dataStore.data.first()[longPreferencesKey(KEY_LAST_WARDROBE)]
            wardrobeRepository.observeWardrobes().collect { all ->
                _uiState.update { state ->
                    // 已选值失效（被删）时退回默认优先级：
                    // 当前已选 > 上次使用 > sortOrder 首个
                    val selected = state.selectedId
                        ?.takeIf { id -> all.any { it.id == id } }
                        ?: currentSelected?.takeIf { id -> all.any { it.id == id } }
                        ?: lastUsed?.takeIf { last -> all.any { it.id == last } }
                        ?: all.firstOrNull()?.id
                    state.copy(
                        wardrobes = all,
                        selectedId = selected,
                        isEmpty = all.isEmpty(),
                    )
                }
            }
        }
    }

    fun onSelect(wardrobeId: Long) {
        _uiState.update { it.copy(selectedId = wardrobeId) }
    }

    /**
     * 确认选择：写回 DataStore 后回调。
     *
     * @param targetId 显式指定要选中的衣柜（弹层内新建成功时用）。
     *   列表里还没有它（Flow 尚未重发）时**直接查库取对象**，不靠列表快照兜底——
     *   这正是 R-04 的失败路径。
     *
     * 写库失败不阻塞选择本身——lastWardrobeId 只是便利性预填，丢了下次多一步而已。
     */
    fun confirm(onPicked: (Wardrobe) -> Unit, targetId: Long? = null) {
        val id = targetId ?: _uiState.value.selectedId ?: return
        viewModelScope.launch {
            val picked = _uiState.value.wardrobes.firstOrNull { it.id == id }
                ?: wardrobeRepository.getWardrobe(id)
                ?: return@launch
            _uiState.update { it.copy(selectedId = picked.id) }
            runCatching {
                dataStore.edit { prefs -> prefs[longPreferencesKey(KEY_LAST_WARDROBE)] = picked.id }
            }
            onPicked(picked)
        }
    }
}

/** 选择衣柜弹层 UI 状态 */
data class WardrobePickerUiState(
    val wardrobes: List<Wardrobe> = emptyList(),
    val selectedId: Long? = null,
    val isEmpty: Boolean = true,
)
