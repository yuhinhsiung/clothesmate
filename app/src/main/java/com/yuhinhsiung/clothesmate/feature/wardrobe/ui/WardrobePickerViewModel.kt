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

    /**
     * @param currentSelected 当前已选的衣柜 id（编辑场景传；新建传 null）
     */
    fun load(currentSelected: Long? = null) {
        viewModelScope.launch {
            val all = wardrobeRepository.getAllWardrobes()
            val lastUsed = dataStore.data.first()[longPreferencesKey(KEY_LAST_WARDROBE)]
            // 优先级：当前已选 > 上次使用 > 首个（sortOrder 升序的首个）
            val default = currentSelected
                ?: lastUsed?.takeIf { last -> all.any { it.id == last } }
                ?: all.firstOrNull()?.id

            _uiState.update {
                it.copy(
                    wardrobes = all,
                    selectedId = default,
                    isEmpty = all.isEmpty(),
                )
            }
        }
    }

    fun onSelect(wardrobeId: Long) {
        _uiState.update { it.copy(selectedId = wardrobeId) }
    }

    /**
     * 确认选择：写回 DataStore 后回调。
     *
     * 写库失败不阻塞选择本身——lastWardrobeId 只是便利性预填，丢了下次多一步而已。
     */
    fun confirm(onPicked: (Wardrobe) -> Unit) {
        val state = _uiState.value
        val picked = state.wardrobes.firstOrNull { it.id == state.selectedId } ?: return
        viewModelScope.launch {
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
