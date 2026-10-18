package com.yuhinhsiung.clothesmate.feature.outfit.ui

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.OutfitCategory
import com.yuhinhsiung.clothesmate.core.model.OutfitDraft
import com.yuhinhsiung.clothesmate.core.model.TagType
import com.yuhinhsiung.clothesmate.core.model.sortTagsByFrequency
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.feature.outfit.data.OutfitRepository
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * 穿搭表单 ViewModel —— M1-18 创建流程 / 编辑复用（路由 [OutfitForm]）。
 *
 * ## 照片的时序（与衣柜封面 / 录入衣物同款「暂存 → 归位」）
 *
 * - 选图（相册多选 / 拍照）先压缩进暂存区 `cache/tmp_images/`，展示用暂存绝对路径；
 * - **创建**：insert 穿搭拿到 id → 按顺序归位到 `outfit/{id}/{index}.jpg` → 回写 photoPaths；
 * - **编辑**：既有照片保留相对路径；新暂存图归位到「现有最大 index + 1」起；
 *   移除既有照片**不立刻删文件**（放弃编辑可无损恢复），保存成功后才定向删除。
 *
 * ## 校验与词典（M1-21）
 *
 * 未选任何衣物时禁用保存（UI 文档 §7.4）；自定义风格 / 场景标签随保存写入
 * tag_dictionary（trim、type+value 唯一、频率递增）。
 */
@HiltViewModel
class OutfitFormViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val imageStore: ImageStore,
    private val imageStorage: ImageStorage,
    private val tagDictionaryDao: TagDictionaryDao,
    private val clothingDao: com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao,
    private val navResultBus: com.yuhinhsiung.clothesmate.ui.navigation.NavResultBus,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: OutfitForm = savedStateHandle.toRoute()
    private val editingId: Long? = route.outfitId

    private val _uiState = MutableStateFlow(OutfitFormUiState(isEdit = editingId != null))
    val uiState: StateFlow<OutfitFormUiState> = _uiState.asStateFlow()

    /** 暂存中的新照片（顺序即列表顺序） */
    private val stagedPhotos = mutableListOf<StagedImage>()

    /** 草稿已清（保存/放弃）；此后不再写回 SavedStateHandle */
    private var draftCleared = false

    /** 初始数据（编辑预填 / 首批照片）就绪前不写草稿，避免「空表单」假草稿 */
    private var draftReady = false

    /** 已选衣物缩略图联查（确认页横滑列 + 编号 chip） */
    val clothingById: StateFlow<Map<Long, ClothingItem>> = clothingDao.observeAll()
        .map { items -> items.associate { it.id to it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val categories: StateFlow<List<OutfitCategory>> = outfitRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val styleSuggestions: StateFlow<List<String>> = suggestionsFlow(TagType.Style.key)
    val sceneSuggestions: StateFlow<List<String>> = suggestionsFlow(TagType.Scene.key)

    private fun suggestionsFlow(type: String) = tagDictionaryDao.observeByType(type)
        .map { rows -> sortTagsByFrequency(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // M2-08 / B-18：优先恢复被系统回收前的草稿
        val restored = OutfitFormDraft.decode(savedStateHandle[OutfitFormDraft.KEY])
        if (restored != null) {
            restoreDraft(restored)
        } else if (editingId != null) {
            loadForEdit(editingId)
        } else {
            loadInitialPhotos()
            applyPreselectedItems()
        }
        viewModelScope.launch {
            uiState.collect { state ->
                if (draftReady && !draftCleared) {
                    savedStateHandle[OutfitFormDraft.KEY] = OutfitFormDraft.encode(
                        state = state,
                        stagedPhotoPaths = stagedPhotos.map { it.file.path },
                    )
                }
            }
        }
        // review B-01/B-19：选择器回传改走 NavResultBus（SavedStateHandle 回传在本工程
        // 三实例真机实测全部失效，见 review/2026-10-17_1015_review.md）。VM 在返回栈下
        // 始终存活，收集不受「表单被选择器盖住」影响；回传 = 完整最终列表（选择器已用
        // [seedPicker] 预填当前已选），直接替换。
        viewModelScope.launch {
            navResultBus.pickedItemIds().collect { picked ->
                if (picked != null) {
                    // userModified 由「与 baseline 比对」统一算出（review R-05），
                    // 这里只落值：选完没改就是没改，不该弹放弃确认
                    _uiState.update { it.copy(selectedItemIds = picked) }
                    navResultBus.consumePickedItemIds()
                }
            }
        }
        // 拍照追加回传（B-01 三实例表的第 3 行，同根因）
        viewModelScope.launch {
            navResultBus.appendCapturedUri().collect { uri ->
                if (!uri.isNullOrBlank()) {
                    onAppendCaptured(uri)
                    navResultBus.consumeAppendCaptured()
                }
            }
        }
    }

    /**
     * M3-04 兜底「存为穿搭」：带入推荐组合中的衣物，并同步 baseline，
     * 使其不被误判为「用户已修改」（review R-05 的脏判定口径）。
     */
    private fun applyPreselectedItems() {
        val ids = route.preselectedItemIds
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.distinct()
            .orEmpty()
        if (ids.isEmpty()) return
        _uiState.update { state ->
            val filled = state.copy(selectedItemIds = ids)
            filled.copy(baseline = filled.snapshot())
        }
    }

    /** 恢复草稿：暂存照片按路径校验，缺失的只丢那一张，其余字段照常。 */
    private fun restoreDraft(draft: OutfitFormDraft) {
        stagedPhotos.clear()
        draft.stagedPhotoPaths.forEach { path ->
            val file = java.io.File(path)
            if (file.exists() && file.length() > 0) {
                stagedPhotos += StagedImage(file = file, byteSize = file.length())
            }
        }
        _uiState.value = draft.toUiState(
            base = OutfitFormUiState(isEdit = editingId != null),
            stagedPaths = stagedPhotos.map { it.file.path },
        )
        draftReady = true
    }

    /**
     * 打开衣物选择器前发布种子：当前已选（含顺序）→ 选择器预填（B-19 修复：
     * 「编辑已含 3 件的穿搭 → 选择器显示 0 件 → 只勾第 4 件 → 原 3 件被静默丢弃」）。
     */
    fun seedPicker() {
        navResultBus.publishPickerSeed(_uiState.value.selectedItemIds)
    }

    /** 创建：入口 FAB 面板带来的首批照片（\n 分隔 Uri），逐张压缩进暂存区 */
    private fun loadInitialPhotos() {
        val uris = route.photoUris?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        if (uris.isEmpty()) {
            draftReady = true
            return
        }
        _uiState.update { it.copy(addingPhoto = true) }
        viewModelScope.launch {
            runCatching {
                uris.map { uri -> imageStore.stash(uri.toUri()) }
            }.onSuccess { staged ->
                stagedPhotos += staged
                _uiState.update {
                    it.copy(
                        addingPhoto = false,
                        stagedDisplayPaths = stagedPhotos.map { s -> s.file.path },
                    )
                }
                draftReady = true
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(
                        addingPhoto = false,
                        error = failure.message ?: "图片处理失败，请重试",
                    )
                }
                draftReady = true
            }
        }
    }

    private fun loadForEdit(outfitId: Long) {
        viewModelScope.launch {
            val outfit = outfitRepository.getOutfit(outfitId)
            if (outfit == null) {
                _uiState.update { it.copy(notFound = true) }
                return@launch
            }
            _uiState.update { loaded ->
                val filled = loaded.copy(
                    existingPaths = outfit.photoPaths,
                    selectedItemIds = outfitRepository.getClothingIds(outfitId),
                    selectedCategoryIds = outfitRepository.getCategoryIdsForOutfit(outfitId).toSet(),
                    styleTags = outfit.styleTags,
                    sceneTags = outfit.sceneTags,
                    seasonTags = outfit.seasonTags,
                    note = outfit.note.orEmpty(),
                )
                // 预填完成即固定 baseline：预填本身不算「用户改过」（review R-05）
                filled.copy(baseline = filled.snapshot())
            }
            draftReady = true
        }
    }

    // ── 照片操作 ────────────────────────────────────────────────

    /** 相册追加（多选，上限 9；超出容量的部分静默截断） */
    fun onPhotosPicked(uris: List<Uri>) {
        val remaining = OutfitFormUiState.MAX_OUTFIT_PHOTOS -
            (_uiState.value.existingPaths.size + stagedPhotos.size)
        val accepted = uris.take(remaining.coerceAtLeast(0))
        if (accepted.isEmpty()) return
        _uiState.update { it.copy(addingPhoto = true, error = null) }
        viewModelScope.launch {
            runCatching { accepted.map { imageStore.stash(it) } }
                .onSuccess { staged ->
                    stagedPhotos += staged
                    _uiState.update {
                        it.copy(
                            addingPhoto = false,
                            stagedDisplayPaths = stagedPhotos.map { s -> s.file.path },
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            addingPhoto = false,
                            error = failure.message ?: "图片处理失败，请重试",
                        )
                    }
                }
        }
    }

    /** 拍照回传由 NavResultBus 收集器直连 [onPhotosPicked]（B-01 修复后不再单独暴露） */
    private fun onAppendCaptured(uri: String) {
        onPhotosPicked(listOf(uri.toUri()))
    }

    /** 移除第 [position] 张。暂存图立即清理；既有照片只在保存成功后删文件。 */
    fun removePhoto(position: Int) {
        val state = _uiState.value
        val stagedCount = stagedPhotos.size
        when {
            // 尾部起的 staged 区段
            position >= state.existingPaths.size -> {
                val stagedIndex = position - state.existingPaths.size
                if (stagedIndex in stagedPhotos.indices) {
                    val removed = stagedPhotos.removeAt(stagedIndex)
                    viewModelScope.launch { imageStore.clearStaged(removed) }
                }
                _uiState.update { it.copy(stagedDisplayPaths = stagedPhotos.map { s -> s.file.path }) }
            }

            else -> {
                val removed = state.existingPaths[position]
                _uiState.update {
                    it.copy(
                        existingPaths = it.existingPaths - removed,
                        removedExistingPaths = it.removedExistingPaths + removed,
                    )
                }
            }
        }
    }

    /** 展示列表 = 既有（编辑）+ 暂存（新加）。封面 = 首图（首图移除后顺延，§7.4） */
    fun displayPhotos(state: OutfitFormUiState): List<String> =
        state.existingPaths + state.stagedDisplayPaths

    // ── 字段变更 ────────────────────────────────────────────────

    fun onToggleItem(itemId: Long) = update {
        it.copy(
            selectedItemIds = if (itemId in it.selectedItemIds) {
                it.selectedItemIds - itemId
            } else {
                it.selectedItemIds + itemId
            },
        )
    }

    fun onToggleCategory(categoryId: Long) = update {
        it.copy(
            selectedCategoryIds = if (categoryId in it.selectedCategoryIds) {
                it.selectedCategoryIds - categoryId
            } else {
                it.selectedCategoryIds + categoryId
            },
        )
    }

    /**
     * 现场新建分类（M1-18「＋ 新建分类」：输入即建并选中）。
     *
     * **策略（review R-05 要求明确记录）**：分类是全局词表，创建即落库；
     * 之后放弃这套穿搭**不回滚分类**。理由是分类本身是用户资产（别处也能用），
     * 而「放弃穿搭」只该丢弃这次的表单草稿。此策略与本条修复一并登记。
     */
    fun createAndSelectCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { outfitRepository.createCategory(trimmed) }
                .onSuccess { id -> _uiState.update { it.copy(selectedCategoryIds = it.selectedCategoryIds + id) } }
                .onFailure { failure -> _uiState.update { it.copy(error = failure.message) } }
        }
    }

    fun onToggleStyle(tag: String) = update { it.copy(styleTags = it.styleTags.toggle(tag)) }
    fun onToggleScene(tag: String) = update { it.copy(sceneTags = it.sceneTags.toggle(tag)) }
    fun onToggleSeason(key: String) = update { it.copy(seasonTags = it.seasonTags.toggle(key)) }

    /** 自定义风格标签（M1-21）：trim 后进已选，保存时统一写词典 */
    fun addCustomStyleTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        update { s -> if (tag in s.styleTags) s else s.copy(styleTags = s.styleTags + tag) }
    }

    /** 自定义场景标签（M1-21）：同上 */
    fun addCustomSceneTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        update { s -> if (tag in s.sceneTags) s else s.copy(sceneTags = s.sceneTags + tag) }
    }

    fun onNoteChange(value: String) = update { it.copy(note = value) }

    fun dismissError() = update { it.copy(error = null) }

    private fun List<String>.toggle(tag: String): List<String> =
        if (tag in this) this - tag else this + tag

    /**
     * 字段变更的统一入口。**不再无条件置 `userModified`**（review R-05）：改动与否
     * 由 [OutfitFormUiState.userModified] 拿当前状态和 baseline 比出来，
     * 照片、分类这些走 `_uiState.update` 直连的路径因此也自然纳入脏判定。
     */
    private inline fun update(transform: (OutfitFormUiState) -> OutfitFormUiState) {
        _uiState.update { transform(it) }
    }

    // ── 保存 ────────────────────────────────────────────────────

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                if (editingId != null) {
                    saveEdit(state)
                } else {
                    saveCreate(state)
                }
            }
                .onSuccess {
                    clearDraft()
                    _uiState.update { it.copy(saving = false) }
                    onSaved()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(saving = false, error = failure.message ?: "保存失败，请重试")
                    }
                }
        }
    }

    private suspend fun saveCreate(state: OutfitFormUiState): Long {
        // review R-13：主行 + 衣物关联 + 分类关联已在 Repository 内部**同事务**提交。
        // 这里只剩「DB 与文件」两界——事务管不到文件，用显式补偿。
        val id = outfitRepository.createOutfit(
            OutfitDraft(
                photoPaths = emptyList(),
                styleTags = state.styleTags,
                sceneTags = state.sceneTags,
                seasonTags = state.seasonTags,
                note = state.note.trim().takeIf { it.isNotEmpty() },
                clothingIds = state.selectedItemIds,
                categoryIds = state.selectedCategoryIds.toList(),
            ),
        )
        // review R-14：归位/回写失败时补偿回滚。
        // 关键是**先把已归位的图退回暂存区**——promote 会 consume 暂存源，第 2 张失败时
        // 第 1 张的暂存文件已经没了；只删归位产物的话草稿会指向不存在的文件，
        // 用户按提示重试仍然失败。退回之后草稿恢复成「什么都没发生」的可重试状态，
        // 同时也不会留下孤儿图片文件。
        val promoted = mutableListOf<String>()
        try {
            stagedPhotos.forEachIndexed { index, staged ->
                promoted += imageStore.promote(staged, imageStorage.outfitPhotoFile(id, index))
            }
            if (promoted.isNotEmpty()) {
                outfitRepository.updatePhotoPaths(id, promoted)
            }
        } catch (failure: Throwable) {
            runCatching {
                stagedPhotos.forEachIndexed { index, staged ->
                    imageStore.restoreStaged(staged, imageStorage.outfitPhotoFile(id, index))
                }
                outfitRepository.deleteOutfit(id)
            }.onFailure { rollbackFailure ->
                android.util.Log.e("OutfitForm", "建档回滚失败，残留无图穿搭 id=$id", rollbackFailure)
            }
            throw failure
        }
        recordCustomTags(state)
        return id
    }

    private suspend fun saveEdit(state: OutfitFormUiState): Long {
        val outfitId = requireNotNull(editingId)
        // 新暂存图归位到现有最大 index + 1 起，避免覆盖既有文件
        val nextIndex = (state.existingPaths + state.removedExistingPaths)
            .mapNotNull(::indexOfPhotoPath)
            .maxOrNull()
            ?.plus(1) ?: 0
        val promoted = mutableListOf<String>()
        try {
            stagedPhotos.forEachIndexed { offset, staged ->
                promoted += imageStore.promote(
                    staged,
                    imageStorage.outfitPhotoFile(outfitId, nextIndex + offset),
                )
            }
            // review R-13：主行更新与两张关联表的「先删后插」已在 Repository 事务里，
            // 失败则一行未改；这里只需把刚归位的图退回暂存区，草稿即可原地重试
            outfitRepository.updateOutfit(
                outfitId,
                OutfitDraft(
                    photoPaths = state.existingPaths + promoted,
                    styleTags = state.styleTags,
                    sceneTags = state.sceneTags,
                    seasonTags = state.seasonTags,
                    note = state.note.trim().takeIf { it.isNotEmpty() },
                    clothingIds = state.selectedItemIds,
                    categoryIds = state.selectedCategoryIds.toList(),
                ),
            )
        } catch (failure: Throwable) {
            runCatching {
                stagedPhotos.forEachIndexed { offset, staged ->
                    imageStore.restoreStaged(
                        staged,
                        imageStorage.outfitPhotoFile(outfitId, nextIndex + offset),
                    )
                }
            }.onFailure { rollbackFailure ->
                android.util.Log.e("OutfitForm", "编辑失败后暂存图退回失败 id=$outfitId", rollbackFailure)
            }
            throw failure
        }
        // 保存成功后才真正删除被移除的既有照片文件（编辑期间放弃可无损恢复）
        state.removedExistingPaths.forEach { path ->
            indexOfPhotoPath(path)?.let { index ->
                imageStorage.deleteOutfitPhoto(outfitId, index)
            }
        }
        recordCustomTags(state)
        return outfitId
    }

    /** 相对路径 `outfit/{id}/{index}.jpg` → index（M1-18 定向删除用） */
    private fun indexOfPhotoPath(relativePath: String): Int? =
        relativePath.substringAfterLast('/')
            .substringBeforeLast('.')
            .toIntOrNull()

    /**
     * 选中的自定义风格 / 场景标签写入词典（M1-21）。
     *
     * review R-14：**词典是辅助索引，不是用户的数据**。原先它排在主记录保存之后、
     * 补偿块之外，登记失败会一路冒泡成「保存失败」——可穿搭其实已经存好了，
     * 用户以为没存、再点一次保存就多出一条重复记录。现在失败只留日志，
     * 保存成功的语义与主记录保持一致。
     */
    private suspend fun recordCustomTags(state: OutfitFormUiState) {
        runCatching {
            state.styleTags.forEach {
                val tag = it.trim()
                if (tag.isNotEmpty()) tagDictionaryDao.insertOrIncrement(TagType.Style.key, tag, isPreset = false)
            }
            state.sceneTags.forEach {
                val tag = it.trim()
                if (tag.isNotEmpty()) tagDictionaryDao.insertOrIncrement(TagType.Scene.key, tag, isPreset = false)
            }
        }.onFailure { failure ->
            android.util.Log.w(TAG, "标签词典登记失败（穿搭已保存，仅词典缺词）", failure)
        }
    }

    /** 放弃 / 关闭：清掉本次会话的暂存图（M1-15 同规则）。
     *
     * review R-05：所有「不保存就离开」的路径都必须走这里——包括**没有改动**时
     * 直接返回的那条。原先只有弹层里点了「放弃」才调 discard()，直接返回路径
     * 会把暂存图留在 `cache/tmp_images/` 里等系统回收。
     */
    fun discard() {
        clearDraft()
        val staged = stagedPhotos.toList()
        stagedPhotos.clear()
        viewModelScope.launch {
            staged.forEach { imageStore.clearStaged(it) }
        }
    }

    /** 保存/放弃后清草稿 */
    private fun clearDraft() {
        draftCleared = true
        savedStateHandle.remove<String>(OutfitFormDraft.KEY)
    }

    private companion object {
        const val TAG = "OutfitForm"
    }
}

/**
 * 穿搭表单 UI 状态。
 *
 * 照片分两段：`existingPaths`（编辑模式保留的既有相对路径）+ `stagedDisplayPaths`
 * （新选暂存图绝对路径）。封面 = 首图；首图移除后顺延（§7.4）。
 */
data class OutfitFormUiState(
    val isEdit: Boolean = false,
    val existingPaths: List<String> = emptyList(),
    val stagedDisplayPaths: List<String> = emptyList(),
    /** 编辑模式中被移除的既有照片（保存成功后删文件；放弃则无损） */
    val removedExistingPaths: List<String> = emptyList(),
    val addingPhoto: Boolean = false,
    /** 已选衣物，顺序即选择顺序（衣物选择器序号圆点与确认页横滑列一致，§7.4） */
    val selectedItemIds: List<Long> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val styleTags: List<String> = emptyList(),
    val sceneTags: List<String> = emptyList(),
    val seasonTags: List<String> = emptyList(),
    val note: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val notFound: Boolean = false,
    /**
     * 进入表单时的初始快照（新建 = 全空；编辑 = 预填完成的那一刻）。
     * [userModified] 拿它与当前状态比对（review R-05）。
     */
    val baseline: OutfitFormBaseline = OutfitFormBaseline(),
) {
    /** 上限 9 张（§7.4 / §3.2） */
    val photoCount: Int get() = existingPaths.size + stagedDisplayPaths.size
    val canAddPhoto: Boolean get() = photoCount < MAX_OUTFIT_PHOTOS && !addingPhoto

    /** 未选衣物禁用保存 + 按钮上方提示（§7.4） */
    val canSave: Boolean
        get() = selectedItemIds.isNotEmpty() && !addingPhoto && !saving && !notFound

    /**
     * 是否有未保存的改动（M1-15 返回确认的唯一依据）。
     *
     * review R-05：原实现靠一个 `userModified` 布尔，只有走字段 setter 才置位——
     * **相册/拍照追加、移除照片、现场新建并选中分类都绕过了它**，于是「只改了照片」
     * 的返回直接丢弃、没有确认。现在改成拿当前状态和 [baseline] 逐项比对：
     * 照片、分类、衣物、标签、备注任何一项变了都算，不可能再漏。
     *
     * `stagedDisplayPaths` 非空 = 本次会话新增过照片（edge：加完又删光则回到不脏，
     * 与「什么都没改」等价，正确）。
     */
    val userModified: Boolean
        get() = existingPaths != baseline.existingPaths ||
            stagedDisplayPaths.isNotEmpty() ||
            selectedItemIds != baseline.selectedItemIds ||
            selectedCategoryIds != baseline.selectedCategoryIds ||
            styleTags != baseline.styleTags ||
            sceneTags != baseline.sceneTags ||
            seasonTags != baseline.seasonTags ||
            note != baseline.note

    /** 当前状态的可比对快照（预填完成后用它固定 baseline） */
    fun snapshot(): OutfitFormBaseline = OutfitFormBaseline(
        existingPaths = existingPaths,
        selectedItemIds = selectedItemIds,
        selectedCategoryIds = selectedCategoryIds,
        styleTags = styleTags,
        sceneTags = sceneTags,
        seasonTags = seasonTags,
        note = note,
    )

    companion object {
        const val MAX_OUTFIT_PHOTOS = 9
    }
}

/** 表单初始快照（review R-05 的脏判定基准）。字段与 [OutfitFormUiState] 中「用户可改」的部分一一对应。 */
@Serializable
data class OutfitFormBaseline(
    val existingPaths: List<String> = emptyList(),
    val selectedItemIds: List<Long> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val styleTags: List<String> = emptyList(),
    val sceneTags: List<String> = emptyList(),
    val seasonTags: List<String> = emptyList(),
    val note: String = "",
)
