package com.yuhinhsiung.clothesmate.feature.addclothing.ui

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.core.image.BackgroundRemover
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.ui.navigation.CutoutPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 抠图预览页 ViewModel —— M2-01 / UI 文档 §7.3。
 *
 * 时序：拍照/选图 → 进来即跑端上抠图 → 落一张**暂存 PNG** → 确认后把暂存路径传给
 * 确认页（保存拿到 id 时再归位到 `clothing/cutout/{id}.png`）。
 *
 * 失败/超时：状态变为 [CutoutPhase.Failed]，页面只保留「重拍 / 用原图」，
 * **不阻断录入**（C-01 兜底口径）。离开页面时若尚未交接，删掉暂存 PNG。
 */
@HiltViewModel
class CutoutPreviewViewModel @Inject constructor(
    private val backgroundRemover: BackgroundRemover,
    private val imageStore: ImageStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: CutoutPreview = savedStateHandle.toRoute()

    /** 原图 Uri（预览页「原图」分段的展示源） */
    val sourceUri: String = route.imageUri

    /** 衣柜预选（透传给确认页） */
    val wardrobeId: Long? = route.wardrobeId

    private val _uiState = MutableStateFlow(CutoutPreviewUiState())
    val uiState: StateFlow<CutoutPreviewUiState> = _uiState.asStateFlow()

    private var staged: StagedImage? = null
    private var job: Job? = null

    /** 已把暂存图交给确认页（或明确放弃），onCleared 不再删文件 */
    private var handedOff = false

    init {
        process()
    }

    fun process() {
        job?.cancel()
        staged?.file?.delete()
        staged = null
        job = viewModelScope.launch {
            _uiState.value = CutoutPreviewUiState(phase = CutoutPhase.Processing)
            try {
                val bitmap = backgroundRemover.removeBackground(route.imageUri.toUri())
                val stagedResult = try {
                    imageStore.stageCutout(bitmap)
                } finally {
                    bitmap.recycle()
                }
                staged = stagedResult
                _uiState.value = CutoutPreviewUiState(
                    phase = CutoutPhase.Ready,
                    cutoutPath = stagedResult.file.path,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _uiState.value = CutoutPreviewUiState(
                    phase = CutoutPhase.Failed,
                    error = failure.message,
                )
            }
        }
    }

    fun onModeChange(mode: CutoutPreviewMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    /** 左右滑动切换对比（§7.3「支持左右滑动对比」） */
    fun toggleMode() {
        val next = if (_uiState.value.mode == CutoutPreviewMode.Cutout) {
            CutoutPreviewMode.Original
        } else {
            CutoutPreviewMode.Cutout
        }
        onModeChange(next)
    }

    /** 确认：把暂存 PNG 路径交给确认页（由它负责保存时归位）。 */
    fun confirm(onReady: (cutoutPath: String) -> Unit) {
        val path = _uiState.value.cutoutPath ?: return
        handedOff = true
        onReady(path)
    }

    /** 用原图：丢弃抠图暂存，直接进确认页。 */
    fun useOriginal(onReady: () -> Unit) {
        discardStaged()
        handedOff = true
        onReady()
    }

    fun retake(onRetake: () -> Unit) {
        discardStaged()
        handedOff = true
        onRetake()
    }

    private fun discardStaged() {
        job?.cancel()
        // 纯文件删除，量级是毫秒；同步做掉可以保证导航离开后不残留
        staged?.file?.delete()
        staged = null
    }

    override fun onCleared() {
        if (!handedOff) staged?.file?.delete()
    }
}

enum class CutoutPhase {
    Processing,
    Ready,
    Failed,
}

enum class CutoutPreviewMode {
    Cutout,
    Original,
}

data class CutoutPreviewUiState(
    val phase: CutoutPhase = CutoutPhase.Processing,
    val cutoutPath: String? = null,
    val mode: CutoutPreviewMode = CutoutPreviewMode.Cutout,
    val error: String? = null,
)
