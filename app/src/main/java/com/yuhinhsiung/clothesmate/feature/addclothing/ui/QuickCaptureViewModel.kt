package com.yuhinhsiung.clothesmate.feature.addclothing.ui

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.core.di.ApplicationScope
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.ai.QuickCaptureAiSuggestion
import com.yuhinhsiung.clothesmate.core.datastore.QuickCaptureAiSuggestionStore
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.feature.addclothing.data.QuickCaptureWorker
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import com.yuhinhsiung.clothesmate.ui.navigation.QuickCapture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** 快速连拍中的一张；建档完成后立刻出现，抠图/取色在后台继续。 */
data class QuickCaptureShot(
    val id: Long,
    val code: String,
    val previewPath: String,
    val processing: Boolean = true,
    val cutoutFailed: Boolean = false,
    val aiSuggestion: QuickCaptureAiSuggestion? = null,
)

data class QuickCaptureUiState(
    val wardrobes: List<Wardrobe> = emptyList(),
    val wardrobe: Wardrobe? = null,
    val capturing: Boolean = false,
    val showReview: Boolean = false,
    val shots: List<QuickCaptureShot> = emptyList(),
    val pending: Int = 0,
    val ending: Boolean = false,
    val error: String? = null,
) {
    val total: Int get() = shots.size + pending
    val codeRange: String?
        get() {
            val codes = shots.mapNotNull { it.code.toIntOrNull() }
            if (codes.isEmpty()) return null
            return "${codes.min()}-${codes.max()}"
        }
}

sealed interface QuickCaptureEvent {
    data class Done(val wardrobeId: Long) : QuickCaptureEvent
    data class Failed(val message: String) : QuickCaptureEvent
}

/**
 * 快速连拍（M4-02 / 开发文档 §2.2.4）。
 *
 * 每张照片在拍摄时立即建一行、立即发编号；原图先归位，抠图与本地取色在应用级
 * 后台协程继续。用户离开页面后后台仍会完成；删除某张走常规软删除，编号按既定
 * 「不回收」规则保留。
 */
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val codeAllocator: CodeAllocator,
    private val imageStore: ImageStore,
    private val imageStorage: ImageStorage,
    private val aiSuggestionStore: QuickCaptureAiSuggestionStore,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<QuickCapture>()
    private val _uiState = MutableStateFlow(QuickCaptureUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<QuickCaptureEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    /** 选中衣柜后串行分配编号，避免快速连点时编号竞争。 */
    private val captureMutex = Mutex()
    private val pendingCount = AtomicInteger(0)

    init {
        viewModelScope.launch {
            wardrobeRepository.observeWardrobes().collect { list ->
                _uiState.update { state ->
                    val selected = state.wardrobe ?: route.wardrobeId
                        ?.let { id -> list.firstOrNull { it.id == id } }
                    state.copy(
                        wardrobes = list,
                        wardrobe = selected,
                        capturing = state.capturing || selected != null,
                    )
                }
            }
        }
    }

    fun selectWardrobe(id: Long) {
        viewModelScope.launch {
            val wardrobe = wardrobeRepository.getWardrobe(id) ?: return@launch
            _uiState.update { it.copy(wardrobe = wardrobe, capturing = true) }
        }
    }

    fun capture(uri: String) {
        val wardrobe = _uiState.value.wardrobe
        if (wardrobe == null) {
            emitFailure("请先选择衣柜")
            return
        }
        pendingCount.incrementAndGet()
        _uiState.update { it.copy(pending = pendingCount.get(), error = null) }
        // 建档与图片归位走应用级作用域：返回/旋转不应把刚拍的照片留在半成品状态。
        appScope.launch {
            var staged: com.yuhinhsiung.clothesmate.core.image.StagedImage? = null
            var createdId: Long? = null
            var createdCode: String? = null
            try {
                captureMutex.withLock {
                    val prepared = withContext(ioDispatcher) { imageStore.stash(uri.toUri()) }
                    staged = prepared
                    val code = codeAllocator.nextCode()
                    createdCode = code
                    val now = System.currentTimeMillis()
                    val id = clothingRepository.createItem(
                        ClothingItem(
                            id = 0L,
                            code = code,
                            wardrobeId = wardrobe.id,
                            categoryKey = null,
                            colorKeys = emptyList(),
                            styleTags = emptyList(),
                            seasonTags = emptyList(),
                            originalImagePath = null,
                            cutoutImagePath = null,
                            brand = null,
                            name = null,
                            note = null,
                            wearCount = 0,
                            lastWornAt = null,
                            status = ClothingStatus.NORMAL,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    createdId = id
                    val path = withContext(ioDispatcher) {
                        imageStore.promote(prepared, imageStorage.clothingOriginalFile(id))
                    }
                    val saved = clothingRepository.getItem(id)
                        ?: error("建档后无法读取衣物 $id")
                    clothingRepository.updateItem(saved.copy(originalImagePath = path))
                    _uiState.update { state ->
                        state.copy(
                            shots = state.shots + QuickCaptureShot(id, code, path),
                        )
                    }
                    launchProcessing(id)
                }
            } catch (failure: Throwable) {
                // 先补偿再判断取消：应用退出时也不能留下「有行无图」的半成品。
                val id = createdId
                val code = createdCode
                if (id != null && code != null) {
                    runCatching {
                        staged?.let {
                            imageStore.restoreStaged(it, imageStorage.clothingOriginalFile(id))
                        }
                        clothingRepository.rollbackFailedCreate(id, code)
                    }
                } else {
                    staged?.let { runCatching { imageStore.clearStaged(it) } }
                }
                if (failure is CancellationException) throw failure
                emitFailure(failure.message ?: "拍照建档失败，请重试")
            } finally {
                pendingCount.decrementAndGet()
                _uiState.update { state ->
                    val done = state.ending && pendingCount.get() == 0
                    state.copy(
                        pending = pendingCount.get(),
                        showReview = if (done) true else state.showReview,
                        capturing = if (done) false else state.capturing,
                        ending = if (done) false else state.ending,
                    )
                }
            }
        }
    }

    private fun launchProcessing(id: Long) {
        QuickCaptureWorker.enqueue(context, id)
        // UI 的「处理中」跟随 WorkManager 的最终结果；最多等 20 秒，
        // 超时后仍可进入补录，避免后台失败把连拍页卡死。
        viewModelScope.launch {
            repeat(40) {
                delay(500)
                val item = clothingRepository.getItem(id)
                if (item?.cutoutImagePath != null) {
                    markProcessed(id, failed = false)
                    pollAiSuggestion(id)
                    return@launch
                }
            }
            markProcessed(id, failed = true)
        }
    }

    private fun pollAiSuggestion(id: Long) {
        viewModelScope.launch {
            repeat(30) {
                val suggestion = aiSuggestionStore.get(id)
                if (suggestion != null) {
                    _uiState.update { state ->
                        state.copy(
                            shots = state.shots.map {
                                if (it.id == id) it.copy(aiSuggestion = suggestion) else it
                            },
                        )
                    }
                    return@launch
                }
                delay(500)
            }
        }
    }

    fun acceptAiSuggestion(id: Long) {
        val suggestion = _uiState.value.shots.firstOrNull { it.id == id }?.aiSuggestion ?: return
        viewModelScope.launch {
            clothingRepository.applyAiSuggestionIfEmpty(
                id = id,
                category = suggestion.categoryKey,
                styles = suggestion.styleTags,
                seasons = suggestion.seasonTags,
            )
            aiSuggestionStore.remove(id)
            _uiState.update { state ->
                state.copy(
                    shots = state.shots.map {
                        if (it.id == id) it.copy(aiSuggestion = null) else it
                    },
                )
            }
        }
    }

    private fun markProcessed(id: Long, failed: Boolean) {
        _uiState.update { state ->
            state.copy(
                shots = state.shots.map {
                    if (it.id == id) it.copy(processing = false, cutoutFailed = failed) else it
                },
            )
        }
    }

    fun deleteShot(id: Long) {
        viewModelScope.launch {
            clothingRepository.softDelete(id)
            aiSuggestionStore.remove(id)
            _uiState.update { state ->
                state.copy(shots = state.shots.filterNot { it.id == id })
            }
        }
    }

    fun endSession() {
        if (pendingCount.get() > 0) {
            _uiState.update { it.copy(ending = true) }
        } else {
            _uiState.update { it.copy(capturing = false, showReview = true) }
        }
    }

    fun resumeCapture() {
        _uiState.update { it.copy(capturing = true, showReview = false) }
    }

    fun complete() {
        val id = _uiState.value.wardrobe?.id ?: return
        _events.tryEmit(QuickCaptureEvent.Done(id))
    }

    private fun emitFailure(message: String) {
        _uiState.update { it.copy(error = message) }
        _events.tryEmit(QuickCaptureEvent.Failed(message))
    }
}

