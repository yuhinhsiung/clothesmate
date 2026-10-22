package com.yuhinhsiung.clothesmate.feature.settings.ui.diagnostics

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.diagnostics.CrashLogFile
import com.yuhinhsiung.clothesmate.core.diagnostics.CrashLogStore
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiagnosticsUiState(
    val logs: List<CrashLogFile> = emptyList(),
    val loading: Boolean = true,
    val exporting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val store: CrashLogStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val logs = withContext(ioDispatcher) { store.logs() }
            _uiState.update { it.copy(logs = logs, loading = false) }
        }
    }

    fun export(uri: Uri, resolver: ContentResolver) {
        if (_uiState.value.exporting) return
        _uiState.update { it.copy(exporting = true, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                val report = withContext(ioDispatcher) { store.buildReport() }
                withContext(ioDispatcher) {
                    val stream = resolver.openOutputStream(uri)
                        ?: error("无法打开导出文件")
                    stream.use { it.write(report.toByteArray(Charsets.UTF_8)) }
                }
            }
            _uiState.update {
                it.copy(
                    exporting = false,
                    message = if (result.isSuccess) "诊断日志已导出" else "导出失败，请检查存储空间后重试",
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 仅调试包可用的注入入口：验证 handler 写入和导出链路。 */
    fun triggerTestCrash() {
        throw RuntimeException("M5-11 injected test crash: ${System.currentTimeMillis()}")
    }
}
