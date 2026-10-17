package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    /** 当前会话身份（由 UI 在每次「打开弹层」时递增传入），见 [startSession] */
    private var currentSessionId: Int? = null

    /** 递增令牌：会话切换后，旧会话挂起的异步结果一律作废 */
    private var sessionToken: Int = 0

    private var loadJob: Job? = null
    private var coverJob: Job? = null

    /**
     * 由 UI 调用开启/续接会话（review A-01）。
     *
     * **问题**：`LaunchedEffect` 在 Activity 配置重建（旋转、切字号、深色模式切换）后
     * 会重新执行一次，而它之前无条件调 `startCreate/startEdit` —— 于是配置一变，
     * 用户正在填的名称、备注、刚选的封面全被当作用户自己关掉又重开，**草稿被清空**。
     *
     * **修法**：会话身份由 UI 提供且**随配置重建一起保存**（`rememberSaveable` 计数器，
     * 只在「打开弹层」这个用户动作上递增）。VM 记住当前会话 id：
     * 相同 id 到达 = 同一次会话的重建，什么都不做；不同 id = 真正的新会话，才复位。
     *
     * ViewModel 本身在配置重建中存活（挂在 Activity / NavBackStackEntry 的 store 上），
     * 所以草稿本来就在——只要别再把它清掉。进程死亡时 VM 与草稿一同消失，
     * 重建后首次进入会照常初始化。
     *
     * @param sessionId 会话身份；同一次打开在配置重建前后必须相同
     * @param wardrobeId 非空 = 编辑该衣柜（预填）；null = 新建
     */
    fun startSession(sessionId: Int, wardrobeId: Long?) {
        if (currentSessionId == sessionId) return
        currentSessionId = sessionId
        resetForNewSession(wardrobeId)
    }

    /**
     * 开启一次**新建**会话（review R-02）。
     *
     * 弹层 VM 挂在列表页的 owner 上，寿命远长于单次弹层会话。原先只在**成功保存**时
     * 清了 `saving` / `stagedCover`，`editingId / name / note / coverPath` 全部残留——
     * 「编辑 b → 保存 → 点 ＋ 新建」会在同一个 VM 里以 `editingId = b` 打开，
     * 界面上写着「编辑衣柜」、名字还是 b，再保存就是**覆盖旧衣柜**。
     *
     * 会话入口必须显式重置，不能只依赖 `onDismissRequest`（保存成功关闭不走那条回调）。
     * 这是**显式的新会话**（不是配置重建），所以无条件复位。
     */
    fun startCreate() {
        currentSessionId = null
        resetForNewSession(wardrobeId = null)
    }

    /** 开启一次**编辑**会话：先清空上一次会话残留，再预填目标衣柜 */
    fun startEdit(wardrobeId: Long) {
        currentSessionId = null
        resetForNewSession(wardrobeId)
    }

    /**
     * 结束会话（保存成功 / 取消 / 关闭都走这里）：丢弃暂存图并复位全部字段。
     * 与 [startCreate] / [startEdit] 配对，保证下一次打开一定是干净状态。
     */
    fun finishSession() {
        currentSessionId = null
        sessionToken++
        loadJob?.cancel()
        coverJob?.cancel()
        discardStaged()
        _uiState.value = WardrobeEditUiState()
    }

    /**
     * 换会话：作废旧异步结果 →（编辑时）重新预填。
     *
     * 令牌递增是必须的：上一次会话里的 `loadForEdit` / 封面压缩可能还没跑完，
     * 它们回来时若照旧写状态，就会把新会话的字段串掉。
     */
    private fun resetForNewSession(wardrobeId: Long?) {
        sessionToken++
        loadJob?.cancel()
        coverJob?.cancel()
        discardStaged()
        _uiState.value = WardrobeEditUiState()
        if (wardrobeId != null) loadForEdit(wardrobeId)
    }

    /** 进入编辑模式：按 wardrobeId 预填 */
    private fun loadForEdit(wardrobeId: Long) {
        val token = sessionToken
        loadJob = viewModelScope.launch {
            val wardrobe = wardrobeRepository.getWardrobe(wardrobeId) ?: return@launch
            if (token != sessionToken) return@launch
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
        val token = sessionToken
        coverJob = viewModelScope.launch {
            _uiState.update { it.copy(compressing = true, error = null) }
            runCatching { imageStore.stash(uri) }
                .onSuccess { staged ->
                    // 会话已切换：这张图属于上一次会话，直接清掉，别串进新会话
                    if (token != sessionToken) {
                        runCatching { imageStore.clearStaged(staged) }
                        return@launch
                    }
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
                    if (token != sessionToken) return@launch
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
     * ## 提交顺序：**先写库，再动文件**（review R-14 场景 B 修复）
     *
     * 封面路径是**确定值**（`wardrobe_cover/{id}.jpg`），不需要等文件落地就能写库。
     * 反过来「先 promote 再写库」有个致命窗口：promote 会**覆盖同名旧封面**并 consume
     * 掉暂存源，此时若 `updateWardrobe` 失败——旧封面内容没了、草稿也失去可重试的暂存图，
     * 用户既回不到原状又重试不了。
     *
     * 现在的顺序：写库 → promote。失败时：
     * - 写库失败：文件一个都没动，旧封面与暂存图原样保留，草稿可原地重试；
     * - promote 失败：把封面字段回滚成进入编辑时的原值（文件仍是旧内容，库不撒谎）。
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
                    saveEdit(state.editingId, state, name, note)
                    state.editingId
                } else {
                    saveCreate(state, name, note)
                }
            }
                .onSuccess { id ->
                    // review R-02：成功即结束会话（清暂存 + 复位全部字段），
                    // 保证下一次打开弹层是干净的新建态
                    finishSession()
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

    /** 编辑保存：写库 → 动文件（顺序见 [save] 的说明） */
    private suspend fun saveEdit(
        id: Long,
        state: WardrobeEditUiState,
        name: String,
        note: String?,
    ) {
        val staged = state.stagedCover
        val newCoverPath = if (staged != null) {
            requireCoverRelativePath(id)
        } else {
            // 未换图：保留原路径（用户「移除封面」时为 null）
            state.coverPath?.takeIf { it.isNotBlank() }
        }
        wardrobeRepository.updateWardrobe(id, name, note, newCoverPath)

        if (staged != null) {
            try {
                imageStore.promote(staged, imageStorage.wardrobeCoverFile(id))
            } catch (failure: Throwable) {
                // 文件没换成 → 把封面字段还原成进入编辑时的值，别让库指向一张不存在的图。
                // 有旧封面时该路径与原值相同，这一步是空操作。
                runCatching { wardrobeRepository.updateWardrobe(id, name, note, state.originalCoverPath) }
                    .onFailure { t -> android.util.Log.w(LOG_TAG, "封面字段回滚失败 id=$id", t) }
                throw failure
            }
        } else if (newCoverPath == null && state.originalCoverPath != null) {
            // review B-32：编辑中移除了原封面 → 清掉旧文件（换封面由覆盖写不泄漏）
            runCatching { imageStorage.deleteWardrobeCover(id) }
                .onFailure { t -> android.util.Log.w(LOG_TAG, "封面清理失败 id=$id", t) }
        }
    }

    /** 新建：insert 拿真 id → 写库（含封面路径）→ promote → 失败则整体回滚 */
    private suspend fun saveCreate(
        state: WardrobeEditUiState,
        name: String,
        note: String?,
    ): Long {
        val staged = state.stagedCover
        val newId = wardrobeRepository.createWardrobe(name, note, coverImagePath = null)
        try {
            val coverPath = staged?.let { requireCoverRelativePath(newId) }
            wardrobeRepository.updateWardrobe(newId, name, note, coverPath)
            staged?.let { imageStore.promote(it, imageStorage.wardrobeCoverFile(newId)) }
        } catch (failure: Throwable) {
            rollbackCreate(staged, newId, failure)
        }
        return newId
    }

    /**
     * 封面相对路径 —— 由 [ImageStorage.wardrobeCoverFile] 直接推出，**不依赖文件已存在**。
     * 这也是「先写库再动文件」得以成立的前提。
     */
    private fun requireCoverRelativePath(wardrobeId: Long): String =
        imageStorage.toRelative(imageStorage.wardrobeCoverFile(wardrobeId))
            ?: error("封面路径不在 images 根目录内：${imageStorage.wardrobeCoverFile(wardrobeId).path}")

    /**
     * 新建衣柜失败时的补偿（review R-14）。
     *
     * 顺序不可交换：**先把封面退回暂存区**（草稿因此重新可重试），再删半截的行。
     * 补偿自身失败只记日志——它不该把原始异常换成一个更难懂的错误。
     */
    private suspend fun rollbackCreate(staged: StagedImage?, newId: Long, cause: Throwable): Nothing {
        runCatching {
            staged?.let { imageStore.restoreStaged(it, imageStorage.wardrobeCoverFile(newId)) }
            wardrobeRepository.deleteWardrobe(newId)
        }.onFailure { rollbackFailure ->
            android.util.Log.e(
                LOG_TAG,
                "衣柜建档回滚失败，残留无封面衣柜 id=$newId",
                rollbackFailure,
            )
        }
        throw cause
    }

    /**
     * 丢弃本会话的暂存图（不重置其它字段）—— [startSession] / [finishSession]
     * 共用的收尾动作。
     */
    private fun discardStaged() {
        val staged = _uiState.value.stagedCover
        if (staged != null) {
            viewModelScope.launch { imageStore.clearStaged(staged) }
        }
    }

    /** 关闭弹层时丢弃本次会话（M1-15）：暂存图清掉 + 状态复位（review R-02） */
    fun onDismiss() = finishSession()

    private companion object {
        const val LOG_TAG = "WardrobeEdit"
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
