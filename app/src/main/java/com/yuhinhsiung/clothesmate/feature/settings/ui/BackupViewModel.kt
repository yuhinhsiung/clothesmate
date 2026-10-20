package com.yuhinhsiung.clothesmate.feature.settings.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.backup.BackupException
import com.yuhinhsiung.clothesmate.core.backup.BackupRepository
import com.yuhinhsiung.clothesmate.core.backup.BackupSummary
import com.yuhinhsiung.clothesmate.core.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val busy: Boolean = false,
    val inspecting: Boolean = false,
    val summary: BackupSummary? = null,
    val importResult: ImportResult? = null,
    val error: String? = null,
) {
    val canImport: Boolean get() = summary != null && !busy && !inspecting
}

sealed interface BackupEvent {
    data class Exported(val summary: BackupSummary) : BackupEvent
    data class Imported(val result: ImportResult) : BackupEvent
    data class Failed(val message: String) : BackupEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun export(uri: Uri, password: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, importResult = null) }
            runCatching { repository.export(uri, password) }
                .onSuccess { summary ->
                    _uiState.update { it.copy(busy = false) }
                    eventChannel.send(BackupEvent.Exported(summary))
                }
                .onFailure { failure ->
                    val message = failure.message ?: "导出失败，请重试"
                    _uiState.update { it.copy(busy = false, error = message) }
                    eventChannel.send(BackupEvent.Failed(message))
                }
        }
    }

    fun inspect(uri: Uri) {
        if (_uiState.value.inspecting || _uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(inspecting = true, error = null, summary = null, importResult = null)
            }
            runCatching { repository.inspect(uri) }
                .onSuccess { summary ->
                    _uiState.update { it.copy(inspecting = false, summary = summary) }
                    if (summary.total == 0) {
                        _uiState.update { it.copy(error = "这个备份里没有可导入的数据") }
                    }
                }
                .onFailure { failure ->
                    val message = failure.message ?: "无法读取备份文件"
                    _uiState.update { it.copy(inspecting = false, error = message) }
                    eventChannel.send(BackupEvent.Failed(message))
                }
        }
    }

    fun import(uri: Uri, password: String) {
        if (!_uiState.value.canImport) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching { repository.import(uri, password) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(busy = false, summary = null, importResult = result)
                    }
                    eventChannel.send(BackupEvent.Imported(result))
                }
                .onFailure { failure ->
                    val message = if (failure is BackupException) {
                        failure.message ?: "导入失败"
                    } else {
                        failure.message ?: "导入失败，请重试"
                    }
                    _uiState.update { it.copy(busy = false, error = message) }
                    eventChannel.send(BackupEvent.Failed(message))
                }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(summary = null, error = null) }
    }
}
