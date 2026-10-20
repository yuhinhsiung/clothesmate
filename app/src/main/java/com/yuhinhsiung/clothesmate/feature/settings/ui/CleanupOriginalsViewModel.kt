package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.image.CleanupEstimate
import com.yuhinhsiung.clothesmate.core.image.CleanupResult
import com.yuhinhsiung.clothesmate.core.image.ImageCleanupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CleanupOriginalsUiState(
    val loading: Boolean = true,
    val estimate: CleanupEstimate? = null,
    val result: CleanupResult? = null,
    val error: String? = null,
)

@HiltViewModel
class CleanupOriginalsViewModel @Inject constructor(
    private val repository: ImageCleanupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupOriginalsUiState())
    val uiState: StateFlow<CleanupOriginalsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.estimate() }
                .onSuccess { estimate -> _uiState.update { it.copy(loading = false, estimate = estimate) } }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(loading = false, error = failure.message ?: "无法统计可回收空间")
                    }
                }
        }
    }

    fun cleanup() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.cleanup() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(loading = false, result = result, estimate = CleanupEstimate(0, 0))
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(loading = false, error = failure.message ?: "清理失败，请重试")
                    }
                }
        }
    }
}
