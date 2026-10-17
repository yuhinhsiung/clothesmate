package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.TagType
import com.yuhinhsiung.clothesmate.core.model.sortTagsByFrequency
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.readLastWardrobeId
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import com.yuhinhsiung.clothesmate.ui.navigation.ClothingForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
 * 衣物表单 ViewModel —— M1-14 录入确认页与 M1-12 编辑页共用。
 *
 * ## 两种模式（路由 [ClothingForm]）
 *
 * - **创建**（`itemId == null`）：上游给图（拍照 / 相册），进入即压缩进暂存区；
 *   编号预填 `CodeAllocator.peekNextCode()`（只读预览，保存时真正发号）；
 *   衣柜预选 = 路由参数 > 上次使用（DataStore）> sortOrder 首个（与选择衣柜弹层同优先级）。
 * - **编辑**（`itemId != null`）：按 id 预填全部字段；照片只读展示（M1 不提供换图）；
 *   编号校验排除自身。
 *
 * ## 编号实时校验（W-06 / UI 文档 §6.7）
 *
 * 每次输入经 [validateCode]：纯数字 + 唯一性（含软删除行，与 UNIQUE 索引同口径）。
 * 校验覆盖手动输入；自动预填值也已校验，保存按钮以 `codeError == null` 为准。
 */
@HiltViewModel
class ClothingFormViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val codeAllocator: CodeAllocator,
    private val imageStore: ImageStore,
    private val imageStorage: ImageStorage,
    private val tagDictionaryDao: TagDictionaryDao,
    private val dataStore: DataStore<Preferences>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: ClothingForm = savedStateHandle.toRoute()
    private val editingId: Long? = route.itemId

    private val _uiState = MutableStateFlow(ClothingFormUiState(isEdit = editingId != null))
    val uiState: StateFlow<ClothingFormUiState> = _uiState.asStateFlow()

    /** 暂存图（创建模式）。放弃/丢弃时清理。 */
    private var staged: StagedImage? = null

    /** 逐键取消上一次编号校验，避免快速输入时的竞态乱序回写 */
    private var codeCheckJob: Job? = null

    /** 风格标签联想（M1-21）：词典里该类型的全部标签，按使用频率降序、与预设同权展示 */
    val styleSuggestions: StateFlow<List<String>> = tagDictionaryDao
        .observeByType(TagType.Style.key)
        .map { rows -> sortTagsByFrequency(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editingId != null) {
            loadForEdit(editingId)
        } else {
            prepareCreate()
        }
    }

    private fun prepareCreate() {
        _uiState.update { it.copy(compressing = true) }
        viewModelScope.launch {
            // 图与默认衣柜互不依赖，并行取
            val stashJob = launch {
                runCatching {
                    route.imageUri?.let { uri -> imageStore.stash(uri.toUri()) }
                }.onSuccess { result ->
                    staged = result
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            imagePath = result?.file?.path,
                        )
                    }
                }.onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            error = failure.message ?: "图片处理失败，请重试",
                        )
                    }
                }
            }
            launch {
                val defaultWardrobe = pickDefaultWardrobe()
                _uiState.update { s -> if (s.wardrobeId == null) s.copy(wardrobeId = defaultWardrobe?.id, wardrobeName = defaultWardrobe?.name) else s }
            }
            launch {
                val code = codeAllocator.peekNextCode()
                _uiState.update { s -> if (!s.codeTouched) s.copy(code = code) else s }
            }
            stashJob.join()
        }
    }

    /** 衣柜预选优先级：路由参数 > 上次使用 > sortOrder 首个（§7.2 同口径） */
    private suspend fun pickDefaultWardrobe(): com.yuhinhsiung.clothesmate.core.model.Wardrobe? {
        val all = wardrobeRepository.getAllWardrobes()
        val byRoute = route.wardrobeId?.let { id -> all.firstOrNull { it.id == id } }
        if (byRoute != null) return byRoute
        val lastUsed = readLastWardrobeId(dataStore)
        val byLastUsed = lastUsed?.let { id -> all.firstOrNull { it.id == id } }
        return byLastUsed ?: all.firstOrNull()
    }

    private fun loadForEdit(itemId: Long) {
        viewModelScope.launch {
            val item = clothingRepository.getItem(itemId)
            if (item == null) {
                _uiState.update { it.copy(notFound = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isEdit = true,
                    imagePath = item.displayImagePath,
                    name = item.name.orEmpty(),
                    categoryKey = item.categoryKey,
                    // 编辑预填：展开既有类别所属大类（B-02 两级上下文完整可见）
                    expandedTopKey = item.categoryKey?.let { key ->
                        ClothingCategory.fromKey(key)?.topKey
                    },
                    colorKeys = item.colorKeys,
                    styleTags = item.styleTags,
                    seasonKeys = item.seasonTags,
                    brand = item.brand.orEmpty(),
                    note = item.note.orEmpty(),
                    wardrobeId = item.wardrobeId,
                    code = item.code,
                )
            }
            viewModelScope.launch {
                _uiState.update { s ->
                    s.copy(
                        wardrobeName = wardrobeRepository.getWardrobe(item.wardrobeId)?.name,
                    )
                }
            }
        }
    }

    // ── 字段变更（全部标记 userModified，M1-15 返回确认依据） ────────

    /**
     * 类别两级选择的**大类行为**（review B-02 修复）：点大类 = 展开该大类的小类行
     * 并保持大类高亮（BD-05 口径）；单小类大类（连衣裙/帽子）直接定值；
     * 再点同一大类 = 收起小类行（保留已选值）。
     *
     * 纯展开/收起**不**标记 userModified（不是用户内容，M1-15 判定只看真实填写）。
     */
    fun onTopCategoryChange(topKey: String) {
        _uiState.update { topCategoryChange(it, topKey) }
    }

    /** 小类选择：写值并保持大类展开（大类高亮由 expandedTopKey 维持）。 */
    fun onSubCategoryChange(subKey: String) = updateState { state ->
        state.copy(categoryKey = if (state.categoryKey == subKey) null else subKey)
    }

    fun onColorToggle(key: String) = updateState { state ->
        val colors = state.colorKeys.toMutableList()
        if (key in colors) colors.remove(key) else colors.add(key)
        state.copy(colorKeys = colors)
    }

    fun onStyleToggle(tag: String) = updateState { state ->
        val tags = state.styleTags.toMutableList()
        if (tag in tags) tags.remove(tag) else tags.add(tag)
        state.copy(styleTags = tags)
    }

    fun onSeasonToggle(key: String) = updateState { state ->
        val tags = state.seasonKeys.toMutableList()
        if (key in tags) tags.remove(key) else tags.add(key)
        state.copy(seasonKeys = tags)
    }

    fun onBrandChange(value: String) = updateState { it.copy(brand = value) }

    /**
     * 自定义名称（review R-08）。
     *
     * M1-12 要求「全字段可改」，而 `clothing_item.name` 原先在编辑页没有输入框，
     * 衣物只能长期用「主色 · 类别」兜底名。**仅编辑模式提供**——创建确认页不强迫
     * 用户起名是既定规则（§2.3.3），保持不动。
     */
    fun onNameChange(value: String) = updateState { it.copy(name = value) }

    fun onNoteChange(value: String) = updateState { it.copy(note = value) }

    /** review B-15：编号改动同样计入 userModified（此前只改编号返回不弹放弃确认） */
    fun onCodeChange(value: String) {
        updateState { it.copy(code = value, codeTouched = true) }
        codeCheckJob?.cancel()
        codeCheckJob = viewModelScope.launch { validateCode(value) }
    }

    fun onWardrobePicked(wardrobeId: Long, wardrobeName: String) =
        updateState { it.copy(wardrobeId = wardrobeId, wardrobeName = wardrobeName) }

    /** 风格标签自定义（M1-21）：trim 后入已选；落库随保存统一 insertOrIncrement */
    fun addCustomStyleTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        updateState { state -> if (tag in state.styleTags) state else state.copy(styleTags = state.styleTags + tag) }
    }

    fun dismissError() = updateState { it.copy(error = null) }

    private inline fun updateState(transform: (ClothingFormUiState) -> ClothingFormUiState) {
        _uiState.update { transform(it).copy(userModified = true) }
    }

    // ── 编号校验 ────────────────────────────────────────────────

    private suspend fun validateCode(code: String) {
        val error = when {
            code.isBlank() -> "编号不能为空"
            !code.all { it.isDigit() } -> "编号必须是纯数字"
            !clothingRepository.isCodeAvailable(code.trimStart('0').ifEmpty { "0" }, excludeId = editingId ?: 0L) ->
                "编号 $code 已被使用"
            else -> null
        }
        _uiState.update { it.copy(codeError = error) }
    }

    // ── 保存 ────────────────────────────────────────────────────

    fun save(onSaved: (wardrobeId: Long, isEdit: Boolean) -> Unit) {
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
                .onSuccess { wardrobeId ->
                    _uiState.update { it.copy(saving = false) }
                    onSaved(wardrobeId, editingId != null)
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = failure.message ?: "保存失败，请重试",
                        )
                    }
                    // review R-14：建档失败会把**本次预约的编号占用**一并撤回，
                    // 于是失败前算出的 codeError 可能已经过期（比如"编号已被使用"其实
                    // 只因为库里有那条半截行）。重新对一次账，避免用户下一次点保存时
                    // 被一个已经不成立的错误挡住。
                    codeCheckJob?.cancel()
                    codeCheckJob = viewModelScope.launch { validateCode(state.code) }
                }
        }
    }

    /**
     * 创建（M1-14）：自动编号走 [CodeAllocator.nextCode]（此时才推进计数器）；
     * 手动编号已在 UI 校验、`createItem` 再兜底一次唯一性。照片两步落位：
     * 先 insert 拿 id，再把暂存图归位到 `clothing/original/{id}.jpg` 并回写路径。
     */
    private suspend fun saveCreate(state: ClothingFormUiState): Long {
        val code = if (state.codeTouched) state.code.trim() else codeAllocator.nextCode()
        val now = System.currentTimeMillis()
        val id = clothingRepository.createItem(
            ClothingItem(
                id = 0L,
                code = code,
                wardrobeId = state.wardrobeId ?: error("所属衣柜不能为空"),
                categoryKey = state.categoryKey,
                colorKeys = state.colorKeys,
                styleTags = state.styleTags,
                seasonTags = state.seasonKeys,
                originalImagePath = null,
                cutoutImagePath = null,
                brand = state.brand.trim().takeIf { it.isNotEmpty() },
                name = null,
                note = state.note.trim().takeIf { it.isNotEmpty() },
                wearCount = 0,
                lastWornAt = null,
                status = ClothingStatus.NORMAL,
                createdAt = now,
                updatedAt = now,
            ),
        )
        // review B-12 / R-14：照片归位/回写失败时补偿回滚——删掉已建行，避免
        // 「孤儿行 + 重试重复建档」（编号计数器已按「删除不回收」语义推进，是既定设计）。
        // review R-14 补上关键一步：**先把已归位的原图退回暂存区**。promote 会 consume
        // 暂存源，只删归位产物的话草稿指向的文件已经不存在，用户按提示重试仍然失败。
        // 退回之后草稿恢复成可重试状态，同时不留孤儿图片文件。
        try {
            staged?.let { image ->
                val path = imageStore.promote(image, imageStorage.clothingOriginalFile(id))
                clothingRepository.getItem(id)?.let { item ->
                    clothingRepository.updateItem(item.copy(originalImagePath = path))
                }
            }
        } catch (failure: Throwable) {
            runCatching {
                // 先把已归位的原图退回暂存区（R-14）：promote 会 consume 暂存源，
                // 只删归位产物的话草稿指向的文件已经不存在，用户按提示重试仍然失败
                staged?.let { image ->
                    imageStore.restoreStaged(image, imageStorage.clothingOriginalFile(id))
                }
                // 再回滚数据库：删行 + **撤回本次预约的编号占用**。
                // 不撤回的话，同一个草稿重试会被「编号 500 已被使用」拦住——
                // 用户什么都没得到，号却永久归给了刚被删掉的那一行（R-14 场景 A）。
                clothingRepository.rollbackFailedCreate(id, code)
            }.onFailure { rollbackFailure ->
                android.util.Log.e(LOG_TAG, "建档回滚失败，残留无图行 id=$id", rollbackFailure)
            }
            throw failure
        }
        recordStyleTags(state.styleTags)
        // canSave 已保证非空；带回去供导航层「保存后回到衣柜详情」（M1-14 验收）
        return requireNotNull(state.wardrobeId)
    }

    /** 编辑（M1-12）：全字段可改；照片与 deletedAt / createdAt 由 Repository 保留原值。 */
    private suspend fun saveEdit(state: ClothingFormUiState): Long {
        val item = clothingRepository.getItem(editingId ?: error("缺少编辑目标"))
            ?: error("衣物不存在或已被删除")
        clothingRepository.updateItem(
            item.copy(
                // review R-08：清空即回到「主色 · 类别」兜底名（displayName 自带兜底）
                name = state.name.trim().takeIf { it.isNotEmpty() },
                categoryKey = state.categoryKey,
                colorKeys = state.colorKeys,
                styleTags = state.styleTags,
                seasonTags = state.seasonKeys,
                brand = state.brand.trim().takeIf { it.isNotEmpty() },
                note = state.note.trim().takeIf { it.isNotEmpty() },
                code = state.code.trim(),
                wardrobeId = state.wardrobeId ?: item.wardrobeId,
            ),
        )
        recordStyleTags(state.styleTags)
        return item.wardrobeId
    }

    /**
     * 选中的风格标签写入词典（M1-21）：已存在则 usageCount +1，不存在则插入。
     *
     * review R-14：词典是辅助索引，写失败只留日志——不能把「衣物已经存好」报成
     * 保存失败，否则用户重试会多建一条。
     */
    private suspend fun recordStyleTags(tags: List<String>) {
        runCatching {
            tags.forEach { tag ->
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) {
                    tagDictionaryDao.insertOrIncrement(TagType.Style.key, trimmed, isPreset = false)
                }
            }
        }.onFailure { failure ->
            android.util.Log.w(LOG_TAG, "风格词典登记失败（衣物已保存，仅词典缺词）", failure)
        }
    }

    /** 放弃（M1-15 [确认]）：清暂存图。 */
    fun discard() {
        val toClear = staged
        staged = null
        if (toClear != null) {
            viewModelScope.launch { imageStore.clearStaged(toClear) }
        }
    }

    private companion object {
        const val LOG_TAG = "ClothingForm"
    }
}

/** 衣物表单 UI 状态（创建 + 编辑共用） */
data class ClothingFormUiState(
    val isEdit: Boolean = false,
    /** 展示用照片路径：创建模式 = 暂存图绝对路径；编辑模式 = 既有相对路径 */
    val imagePath: String? = null,
    val compressing: Boolean = false,
    /**
     * 自定义名称（review R-08）。**仅编辑模式可编辑**：创建确认页按 §2.3.3 不提供名称输入，
     * 空值展示侧回落「主色 · 类别」兜底名。
     */
    val name: String = "",
    val categoryKey: String? = null,
    /** 已展开的大类（review B-02：与 categoryKey 解耦——此前由 categoryKey 反推，
     *  多小类大类点开即置空导致二级行永不渲染、6/8 个大类选不中） */
    val expandedTopKey: String? = null,
    val colorKeys: List<String> = emptyList(),
    val styleTags: List<String> = emptyList(),
    val seasonKeys: List<String> = emptyList(),
    val brand: String = "",
    val note: String = "",
    val wardrobeId: Long? = null,
    val wardrobeName: String? = null,
    val code: String = "",
    val codeTouched: Boolean = false,
    val codeError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val notFound: Boolean = false,
    /** 用户是否动过表单（M1-15 返回确认判定；预填不算） */
    val userModified: Boolean = false,
) {
    /** 可保存：编号合法 + 已选衣柜 + 不在压缩/保存中。保存按钮据此禁用（§6.3 38% 透明）。 */
    val canSave: Boolean
        get() = !compressing && !saving && !notFound &&
            wardrobeId != null && code.isNotBlank() && codeError == null
}

/**
 * 类别两级选择的状态迁移 —— **纯函数**（review B-02 修复的直测载体，JVM 可测）。
 *
 * 语义（BD-05 口径）：点新大类 = 展开其小类行并保持大类高亮；单小类大类
 * （连衣裙/帽子）直接定值；点已展开的大类 = 收起小类行（保留已选值）；
 * 切到别的大类时，原值仅在与新大类同属时才保留（否则清空待选）。
 */
internal fun topCategoryChange(
    state: ClothingFormUiState,
    topKey: String,
): ClothingFormUiState {
    if (state.expandedTopKey == topKey) {
        return state.copy(expandedTopKey = null)
    }
    val subs = ClothingCategory.entries.filter { it.topKey == topKey }
    return state.copy(
        expandedTopKey = topKey,
        categoryKey = if (subs.size == 1) {
            subs.first().key
        } else {
            state.categoryKey.takeIf { key ->
                key != null && ClothingCategory.fromKey(key)?.topKey == topKey
            }
        },
    )
}
