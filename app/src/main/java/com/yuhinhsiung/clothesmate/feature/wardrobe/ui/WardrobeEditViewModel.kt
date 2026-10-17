package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 添加 / 编辑衣柜 —— 开发计划 M1-06 / 开发文档 §2.1.2 W-01/W-02、UI 文档 §7.2。
 *
 * 同一个 ViewModel 服务「新建」与「编辑」两种模式（编辑复用弹层 + 预填，见 UI 文档 §7.2）。
 *
 * ## 封面的归位时序
 *
 * §3.3 规定封面路径带 id（`wardrobe_cover/{id}.jpg`），而 id 要 insert 之后才知道。
 * 所以：选图先压进暂存区 → insert 衣柜拿到真 id → 归位到真路径 → 更新该行的
 * `coverImagePath`。编辑模式 id 已知，可省掉前两步。
 */
@HiltViewModel
class WardrobeEditViewModel @Inject constructor(
    private val wardrobeRepository: WardrobeRepository,
    private val imageStore: ImageStore,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WardrobeEditUiState())
    val uiState: StateFlow<WardrobeEditUiState> = _uiState.asStateFlow()

    /** 进入编辑模式：按 wardrobeId 预填 */
    fun loadForEdit(wardrobeId: Long) {
        viewModelScope.launch {
            val wardrobe = wardrobeRepository.getWardrobe(wardrobeId) ?: return@launch
            _uiState.update {
                it.copy(
                    editingId = wardrobeId,
                    name = wardrobe.name,
                    note = wardrobe.note.orEmpty(),
                    coverPath = wardrobe.coverImagePath,
                    // review B-32：记录进入编辑时的原始封面，保存时判断是否需要
                    // 清旧文件（换封面走 rename 覆盖不泄漏；「移除封面」才会留孤儿文件）
                    originalCoverPath = wardrobe.coverImagePath,
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameTouched = true) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    /** 选封面：压进暂存区，保存时才归位（M1-14 的时序同此） */
    fun onCoverPicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(compressing = true, error = null) }
            runCatching { imageStore.stash(uri) }
                .onSuccess { staged ->
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            stagedCover = staged,
                            // 暂存图立即可见：用户选完要看到效果，不等保存
                            coverPath = staged.file.path,
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            error = failure.message ?: "图片处理失败，请重试",
                        )
                    }
                }
        }
    }

    fun onClearCover() {
        val staged = _uiState.value.stagedCover
        _uiState.update { it.copy(stagedCover = null, coverPath = null) }
        if (staged != null) {
            viewModelScope.launch { imageStore.clearStaged(staged) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 保存。
     *
     * - **编辑**：id 已知 → 归位封面 → update
     * - **新建**：先 insert 拿真 id → 归位封面 → 再 update 一次写 `coverImagePath`
     *   （insert 时传 null，避免为凑路径先 insert 两次）
     *
     * @param onSaved (衣柜 id, 衣柜名)。带名称是为了让上层 Snackbar 能出
     *   「已添加 · {名称}」（UI 文档 §7.2 / review U-r），不必为一句文案去查库。
     */
    fun save(onSaved: (Long, String) -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        val trimmedName = state.name.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                val name = trimmedName
                val note = state.note.trim().takeIf { it.isNotEmpty() }

                if (state.editingId != null) {
                    val cover = promoteCover(state.stagedCover, state.coverPath, state.editingId)
                    wardrobeRepository.updateWardrobe(state.editingId, name, note, cover)
                    // review B-32：编辑中移除了原封面 → 清掉旧文件
                    // （换封面由 rename 原子覆盖不泄漏；清空才留孤儿）
                    if (cover == null && state.originalCoverPath != null) {
                        imageStorage.deleteWardrobeCover(state.editingId)
                    }
                    state.editingId
                } else {
                    // 新建：insert 时封面留空，拿到 id 后再归位并补一次 update
                    val newId = wardrobeRepository.createWardrobe(name, note, coverImagePath = null)
                    val cover = promoteCover(state.stagedCover, state.coverPath, newId)
                    wardrobeRepository.updateWardrobe(newId, name, note, cover)
                    newId
                }
            }
                .onSuccess { id ->
                    _uiState.update { it.copy(saving = false, stagedCover = null) }
                    onSaved(id, trimmedName)
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = failure.message ?: "保存失败，请重试",
                        )
                    }
                }
        }
    }

    /**
     * 封面路径落地。三种情况：
     * ① 有暂存图 → 归位到 `wardrobe_cover/{id}.jpg`，返回相对路径；
     * ② 无暂存图但已有相对路径（编辑时没换图）→ 原样保留；
     * ③ 都没有 → null。
     */
    private suspend fun promoteCover(
        staged: StagedImage?,
        currentPath: String?,
        wardrobeId: Long,
    ): String? {
        if (staged == null) return currentPath?.takeIf { it.isNotBlank() }
        return imageStore.promote(staged, imageStorage.wardrobeCoverFile(wardrobeId))
    }

    /** 关闭弹层时丢弃未保存的暂存图（M1-15） */
    fun onDismiss() {
        val staged = _uiState.value.stagedCover
        if (staged != null) {
            viewModelScope.launch { imageStore.clearStaged(staged) }
        }
        _uiState.value = WardrobeEditUiState()
    }
}

/** 添加 / 编辑衣柜的 UI 状态 */
data class WardrobeEditUiState(
    val editingId: Long? = null,
    val name: String = "",
    val note: String = "",
    /** 当前封面展示用路径。可能是暂存图绝对路径（选完未保存）或相对路径（编辑预填） */
    val coverPath: String? = null,
    /** 进入编辑时的原始封面（review B-32：保存时判断是否需要清旧文件） */
    val originalCoverPath: String? = null,
    val stagedCover: StagedImage? = null,
    val nameTouched: Boolean = false,
    val compressing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** 名称为空即不可保存（UI 文档 §7.2） */
    val canSave: Boolean
        get() = name.isNotBlank() && !compressing && !saving

    /** 名称错误提示：用户动过手且为空时才红字 */
    val showNameError: Boolean
        get() = nameTouched && name.isBlank()

    val isEditing: Boolean
        get() = editingId != null
}
