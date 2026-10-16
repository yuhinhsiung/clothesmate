package com.yuhinhsiung.clothesmate.feature.outfit.ui

import android.net.Uri
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
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.feature.outfit.data.OutfitRepository
import com.yuhinhsiung.clothesmate.ui.navigation.OUTFIT_APPEND_URI_KEY
import com.yuhinhsiung.clothesmate.ui.navigation.OutfitForm
import com.yuhinhsiung.clothesmate.ui.navigation.PICKED_ITEM_IDS_KEY
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: OutfitForm = savedStateHandle.toRoute()
    private val editingId: Long? = route.outfitId
    private val handle: SavedStateHandle = savedStateHandle

    private val _uiState = MutableStateFlow(OutfitFormUiState(isEdit = editingId != null))
    val uiState: StateFlow<OutfitFormUiState> = _uiState.asStateFlow()

    /** 暂存中的新照片（顺序即列表顺序） */
    private val stagedPhotos = mutableListOf<StagedImage>()

    /** 衣物选择器回传结果（经 SavedStateHandle） */
    val pickedItemIds: StateFlow<List<Long>> =
        savedStateHandle.getStateFlow(PICKED_ITEM_IDS_KEY, emptyList())

    /** 拍照追加回传（Capture OUTFIT_APPEND，经 SavedStateHandle） */
    val capturedUri: StateFlow<String> =
        savedStateHandle.getStateFlow(OUTFIT_APPEND_URI_KEY, "")

    fun consumePickedIds() {
        handle[PICKED_ITEM_IDS_KEY] = emptyList<Long>()
    }

    fun consumeCapturedUri() {
        handle[OUTFIT_APPEND_URI_KEY] = ""
    }

    /** 已选衣物缩略图联查（确认页横滑列 + 编号 chip） */
    val clothingById: StateFlow<Map<Long, ClothingItem>> = clothingDao.observeAll()
        .map { items -> items.associate { it.id to it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val categories: StateFlow<List<OutfitCategory>> = outfitRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val styleSuggestions: StateFlow<List<String>> = suggestionsFlow(TagType.Style.key)
    val sceneSuggestions: StateFlow<List<String>> = suggestionsFlow(TagType.Scene.key)

    private fun suggestionsFlow(type: String) = tagDictionaryDao.observeByType(type)
        .map { rows ->
            rows.sortedWith(
                compareByDescending<TagDictionaryEntity> { it.usageCount }.thenBy { it.value },
            ).map { it.value }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editingId != null) {
            loadForEdit(editingId)
        } else {
            loadInitialPhotos()
        }
    }

    /** 创建：入口 FAB 面板带来的首批照片（\n 分隔 Uri），逐张压缩进暂存区 */
    private fun loadInitialPhotos() {
        val uris = route.photoUris?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        if (uris.isEmpty()) return
        _uiState.update { it.copy(addingPhoto = true) }
        viewModelScope.launch {
            runCatching {
                uris.map { uri -> imageStore.stash(Uri.parse(uri)) }
            }.onSuccess { staged ->
                stagedPhotos += staged
                _uiState.update {
                    it.copy(
                        addingPhoto = false,
                        stagedDisplayPaths = stagedPhotos.map { s -> s.file.path },
                    )
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(
                        addingPhoto = false,
                        error = failure.message ?: "图片处理失败，请重试",
                    )
                }
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
            _uiState.update {
                it.copy(
                    existingPaths = outfit.photoPaths,
                    selectedItemIds = outfitRepository.getClothingIds(outfitId),
                    selectedCategoryIds = outfitRepository.getCategoryIdsForOutfit(outfitId).toSet(),
                    styleTags = outfit.styleTags,
                    sceneTags = outfit.sceneTags,
                    seasonTags = outfit.seasonTags,
                    note = outfit.note.orEmpty(),
                )
            }
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

    /** 拍照回传（M1-18「拍照可连续追加」）：SavedStateHandle 观察到后走这里 */
    fun onAppendCaptured(uri: String) {
        onPhotosPicked(listOf(Uri.parse(uri)))
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

    /** 衣物选择器回传：整体替换选择（选择顺序即回传顺序） */
    fun onPickerResult(ids: List<Long>) = update { it.copy(selectedItemIds = ids) }

    fun onToggleCategory(categoryId: Long) = update {
        it.copy(
            selectedCategoryIds = if (categoryId in it.selectedCategoryIds) {
                it.selectedCategoryIds - categoryId
            } else {
                it.selectedCategoryIds + categoryId
            },
        )
    }

    /** 现场新建分类（M1-18「＋ 新建分类」：输入即建并选中） */
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

    private inline fun update(transform: (OutfitFormUiState) -> OutfitFormUiState) {
        _uiState.update { transform(it).copy(userModified = true) }
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
        val promoted = stagedPhotos.mapIndexed { index, staged ->
            imageStore.promote(staged, imageStorage.outfitPhotoFile(id, index))
        }
        if (promoted.isNotEmpty()) {
            outfitRepository.updatePhotoPaths(id, promoted)
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
        val promoted = stagedPhotos.mapIndexed { offset, staged ->
            imageStore.promote(staged, imageStorage.outfitPhotoFile(outfitId, nextIndex + offset))
        }
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

    private suspend fun recordCustomTags(state: OutfitFormUiState) {
        state.styleTags.forEach {
            val tag = it.trim()
            if (tag.isNotEmpty()) tagDictionaryDao.insertOrIncrement(TagType.Style.key, tag, isPreset = false)
        }
        state.sceneTags.forEach {
            val tag = it.trim()
            if (tag.isNotEmpty()) tagDictionaryDao.insertOrIncrement(TagType.Scene.key, tag, isPreset = false)
        }
    }

    /** 放弃 / 关闭：清掉本次会话的暂存图（M1-15 同规则） */
    fun discard() {
        val staged = stagedPhotos.toList()
        stagedPhotos.clear()
        viewModelScope.launch {
            staged.forEach { imageStore.clearStaged(it) }
        }
    }

    private companion object {
        const val PICKED_KEY = "picked_item_ids"
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
    val userModified: Boolean = false,
) {
    /** 上限 9 张（§7.4 / §3.2） */
    val photoCount: Int get() = existingPaths.size + stagedDisplayPaths.size
    val canAddPhoto: Boolean get() = photoCount < MAX_OUTFIT_PHOTOS && !addingPhoto

    /** 未选衣物禁用保存 + 按钮上方提示（§7.4） */
    val canSave: Boolean
        get() = selectedItemIds.isNotEmpty() && !addingPhoto && !saving && !notFound

    companion object {
        const val MAX_OUTFIT_PHOTOS = 9
    }
}
