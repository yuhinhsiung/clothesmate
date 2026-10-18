package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuhinhsiung.clothesmate.core.ai.AiRecognition
import com.yuhinhsiung.clothesmate.core.ai.ClothingRecognizer
import com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.datastore.AiConfig
import com.yuhinhsiung.clothesmate.core.datastore.AiConsent
import com.yuhinhsiung.clothesmate.core.datastore.AiStatus
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.image.BitmapLoader
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageProcessException
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.LocalColorExtractorService
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
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
    private val settingsStore: UserSettingsStore,
    private val recognizer: ClothingRecognizer,
    private val localColorExtractor: LocalColorExtractorService,
    private val bitmapLoader: BitmapLoader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: ClothingForm = savedStateHandle.toRoute()
    private val editingId: Long? = route.itemId

    private val _uiState = MutableStateFlow(ClothingFormUiState(isEdit = editingId != null))
    val uiState: StateFlow<ClothingFormUiState> = _uiState.asStateFlow()

    /** 暂存图（创建模式）。放弃/丢弃时清理。 */
    private var staged: StagedImage? = null

    /** M2-01：抠图预览页交接来的暂存 PNG；保存时归位到 `clothing/cutout/{id}.png` */
    private var stagedCutout: StagedImage? = null

    /** AI 识别 job（取消识别 / 超时 / 页面销毁统一走它） */
    private var aiJob: Job? = null

    /** 本地取色 job */
    private var colorJob: Job? = null

    /** 已成功启动过一次识别（防止设置页返回后重复触发） */
    private var aiStarted = false

    /** 最近一次读取到的 AI 配置与授权（隐私弹窗 / 重试路径用） */
    private var aiConfig: AiConfig = AiConfig()
    private var aiConsent: AiConsent = AiConsent.Unasked

    /** 草稿已清（保存/放弃）；此后不再把状态写回 SavedStateHandle */
    private var draftCleared = false

    /** R08：暂存文件是否已释放，避免 onCleared 重复删除 */
    private var stagedResourcesReleased = false

    /**
     * 初始数据是否已就绪。**编辑模式在 loadForEdit 完成前不能写草稿**——
     * 否则进程恰好死在那几毫秒里，会留下「isEdit=true 但字段全空」的假草稿，
     * 恢复时又跳过 loadForEdit，用户看到一张空表单，保存就会清字段。
     */
    private var draftReady = false

    /** 逐键取消上一次编号校验，避免快速输入时的竞态乱序回写 */
    private var codeCheckJob: Job? = null

    /** 风格标签联想（M1-21）：词典里该类型的全部标签，按使用频率降序、与预设同权展示 */
    val styleSuggestions: StateFlow<List<String>> = tagDictionaryDao
        .observeByType(TagType.Style.key)
        .map { rows -> sortTagsByFrequency(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // M2-08 / B-18：优先恢复「被系统回收前的草稿」（SavedStateHandle 随任务栈存活）
        val restored = ClothingFormDraft.decode(savedStateHandle[ClothingFormDraft.KEY])
        if (restored != null) {
            restoreDraft(restored)
        } else {
            // M2-01：抠图预览页交接来的暂存 PNG（进程内绝对路径，不写库）
            route.cutoutPath?.let { path ->
                File(path).takeIf { it.exists() && it.length() > 0 }?.let {
                    stagedCutout = StagedImage(file = it, byteSize = it.length())
                }
            }
            if (editingId != null) {
                loadForEdit(editingId)
            } else {
                prepareCreate()
            }
        }
        observeAutoFillSettings()
        // 每次状态变化写草稿；进程死亡时由系统随任务栈保存
        viewModelScope.launch {
            uiState.collect { state ->
                if (draftReady && !draftCleared) {
                    savedStateHandle[ClothingFormDraft.KEY] = ClothingFormDraft.encode(
                        state = state,
                        stagedPath = staged?.file?.path,
                        cutoutPath = stagedCutout?.file?.path,
                    )
                }
            }
        }
    }

    /**
     * 恢复草稿：字段直接还原；暂存图按路径校验存在性，缓存被系统清掉时
     * 退回「用路由里的原图重新压缩」，其余已填内容不丢。
     */
    private fun restoreDraft(draft: ClothingFormDraft) {
        val stagedFile = draft.imageStagedPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        staged = stagedFile?.let { StagedImage(file = it, byteSize = it.length()) }
        val cutoutFile = draft.cutoutStagedPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        stagedCutout = cutoutFile?.let { StagedImage(file = it, byteSize = it.length()) }
        _uiState.value = draft.toUiState(ClothingFormUiState(isEdit = editingId != null)).let { state ->
            val hasStaged = staged?.file?.exists() == true
            val hasCutout = stagedCutout?.file?.exists() == true
            state.copy(
                // R02：恢复时必须显式给出图片就绪态；缺失暂存图时先进入处理态，
                // 重新压缩完成前 canSave 必须为 false。
                imageReady = editingId != null || hasStaged,
                compressing = editingId == null && !hasStaged && route.imageUri != null,
                imageUnavailable = editingId == null && !hasStaged && route.imageUri == null,
                // 展示路径按实际存在性重算：草稿里的抠图路径可能已被系统清掉
                imagePath = if (editingId == null) {
                    stagedCutout?.file?.path ?: staged?.file?.path ?: state.imagePath
                } else {
                    state.imagePath
                },
                imageIsCutout = if (editingId == null) hasCutout else state.imageIsCutout,
            )
        }
        draftReady = true

        if (editingId == null) {
            if (staged?.file?.exists() == true) {
                startColorExtraction()
                evaluateAiStart()
            } else if (route.imageUri != null) {
                restashOriginal()
            }
        }
    }

    /**
     * R02：缓存里的暂存原图丢失时重新压缩。期间 compressing=true、imageReady=false，
     * 保存按钮禁用；失败时保留字段草稿并给出「重试 / 返回重新选图」入口。
     */
    private fun restashOriginal() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    compressing = true,
                    imageReady = false,
                    imageUnavailable = false,
                    error = null,
                )
            }
            runCatching { route.imageUri?.let { uri -> imageStore.stash(uri.toUri()) } }
                .onSuccess { result ->
                    staged = result
                    val ready = result != null && result.file.exists()
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            imageReady = ready,
                            imageUnavailable = !ready,
                            imagePath = stagedCutout?.file?.path ?: result?.file?.path,
                            imageIsCutout = stagedCutout != null,
                        )
                    }
                    if (ready) {
                        startColorExtraction()
                        evaluateAiStart()
                    }
                }
                .onFailure { failure ->
                    staged = null
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            imageReady = false,
                            imageUnavailable = true,
                            error = failure.message ?: "图片处理失败，请重试",
                        )
                    }
                }
        }
    }

    /** UI「重试」：重新压缩路由带来的原图；失败仍停在不可保存态 */
    fun retryImageStash() {
        if (editingId != null) return
        if (route.imageUri == null) {
            _uiState.update { it.copy(imageUnavailable = true) }
            return
        }
        restashOriginal()
    }

    private fun prepareCreate() {
        _uiState.update {
            it.copy(compressing = true, imageReady = false, imageUnavailable = false)
        }
        viewModelScope.launch {
            // 图与默认衣柜互不依赖，并行取
            val stashJob = launch {
                runCatching {
                    route.imageUri?.let { uri -> imageStore.stash(uri.toUri()) }
                }.onSuccess { result ->
                    staged = result
                    // R02：没有有效原图时不能进入可保存态（避免保存出无图衣物）
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            imageReady = result != null && result.file.exists(),
                            imageUnavailable = result == null || !result.file.exists(),
                            // 有抠图时确认页展示抠图（透明底铺 muted），否则原图
                            imagePath = stagedCutout?.file?.path ?: result?.file?.path,
                            imageIsCutout = stagedCutout != null,
                        )
                    }
                }.onFailure { failure ->
                    staged = null
                    _uiState.update {
                        it.copy(
                            compressing = false,
                            imageReady = false,
                            imageUnavailable = true,
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
            draftReady = true
            // 图像就绪后才开始本地取色 + AI 识别判断（避免拿空图去跑）
            if (staged != null) {
                startColorExtraction()
                evaluateAiStart()
            }
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
                    imageIsCutout = item.cutoutImagePath != null,
                    // 编辑模式照片只读，不依赖暂存原图；允许保存字段修改
                    imageReady = true,
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
            draftReady = true
            viewModelScope.launch {
                _uiState.update { s ->
                    s.copy(
                        wardrobeName = wardrobeRepository.getWardrobe(item.wardrobeId)?.name,
                    )
                }
            }
        }
    }

    // ── 字段变更（M1-15 脏判定 + M2-04 手填保护） ──────────────
    //
    // 每个字段同时记录两件事：
    // 1. `userModified`：M1-15 返回确认的唯一依据；
    // 2. `touchedFields`：M2-04 的「用户手填字段不被 AI 覆盖」。两者不能合并——
    //    AI 预填会改值但**不算用户填过**，否则 AI 一返回就永远无法再覆盖自己。

    /**
     * 类别两级选择的**大类行为**（review B-02 修复）：点大类 = 展开该大类的小类行
     * 并保持大类高亮（BD-05 口径）；单小类大类（连衣裙/帽子）直接定值；
     * 再点同一大类 = 收起小类行（保留已选值）。
     *
     * 纯展开/收起**不**标记 userModified（不是用户内容，M1-15 判定只看真实填写）；
     * 但用户一旦点大类，就视为「类别已被手填」，AI 不再覆盖。
     */
    fun onTopCategoryChange(topKey: String) {
        _uiState.update { state ->
            topCategoryChange(state, topKey).copy(
                userModified = true,
                touchedFields = state.touchedFields + AutoFillField.CATEGORY,
                categoryAiFilled = false,
            )
        }
    }

    /** 小类选择：写值并保持大类展开（大类高亮由 expandedTopKey 维持）。 */
    fun onSubCategoryChange(subKey: String) = updateField(AutoFillField.CATEGORY) { state ->
        state.copy(categoryKey = if (state.categoryKey == subKey) null else subKey)
            .copy(categoryAiFilled = false)
    }

    fun onColorToggle(key: String) = updateField(AutoFillField.COLORS) { state ->
        val colors = state.colorKeys.toMutableList()
        if (key in colors) colors.remove(key) else colors.add(key)
        state.copy(colorKeys = colors, colorsAutoFilled = false)
    }

    fun onStyleToggle(tag: String) = updateField(AutoFillField.STYLE) { state ->
        val tags = state.styleTags.toMutableList()
        if (tag in tags) tags.remove(tag) else tags.add(tag)
        state.copy(styleTags = tags, styleAiFilled = false)
    }

    fun onSeasonToggle(key: String) = updateField(AutoFillField.SEASON) { state ->
        val tags = state.seasonKeys.toMutableList()
        if (key in tags) tags.remove(key) else tags.add(key)
        state.copy(seasonKeys = tags, seasonAiFilled = false)
    }

    fun onBrandChange(value: String) = updateField(AutoFillField.BRAND) { it.copy(brand = value) }

    /**
     * 自定义名称（review R-08）。
     *
     * M1-12 要求「全字段可改」，而 `clothing_item.name` 原先在编辑页没有输入框，
     * 衣物只能长期用「主色 · 类别」兜底名。**仅编辑模式提供**——创建确认页不强迫
     * 用户起名是既定规则（§2.3.3），保持不动。
     */
    fun onNameChange(value: String) = updateField(AutoFillField.NAME) { it.copy(name = value) }

    fun onNoteChange(value: String) = updateField(AutoFillField.NOTE) { it.copy(note = value) }

    /** review B-15：编号改动同样计入 userModified（此前只改编号返回不弹放弃确认） */
    fun onCodeChange(value: String) {
        updateField(AutoFillField.CODE) { it.copy(code = value, codeTouched = true) }
        codeCheckJob?.cancel()
        codeCheckJob = viewModelScope.launch { validateCode(value) }
    }

    fun onWardrobePicked(wardrobeId: Long, wardrobeName: String) =
        updateField(AutoFillField.WARDROBE) {
            it.copy(wardrobeId = wardrobeId, wardrobeName = wardrobeName)
        }

    /** 风格标签自定义（M1-21）：trim 后入已选；落库随保存统一 insertOrIncrement */
    fun addCustomStyleTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        updateField(AutoFillField.STYLE) { state ->
            // U03：用户自己输入/确认的标签不再是「AI 预填」，必须清掉 ✨ 来源角标
            state.withCustomStyleTag(tag)
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    /** 非 AI 字段的通用更新（记录 touched） */
    private inline fun updateField(
        field: AutoFillField,
        transform: (ClothingFormUiState) -> ClothingFormUiState,
    ) {
        _uiState.update { state ->
            transform(state).copy(
                userModified = true,
                touchedFields = state.touchedFields + field,
            )
        }
    }

    private inline fun updateState(transform: (ClothingFormUiState) -> ClothingFormUiState) {
        _uiState.update { transform(it).copy(userModified = true) }
    }

    // ── M2-02 / M2-04：本地取色 + AI 异步预填 ───────────────────

    /**
     * 本地取色（M2-02）：对抠图结果（无抠图则原图）聚类取主色，写进「主颜色」。
     *
     * 与 AI 识别**并行且互不依赖**：没有 Key 也照常执行（R-3 降级红线）。
     * 用户已经点过颜色 = 手填，不覆盖。
     */
    private fun startColorExtraction() {
        val target = stagedCutout?.file ?: staged?.file ?: return
        colorJob?.cancel()
        colorJob = viewModelScope.launch {
            val keys = runCatching {
                localColorExtractor.extractKeys(Uri.fromFile(target))
            }.getOrNull().orEmpty()
            if (keys.isEmpty()) return@launch
            _uiState.update { state ->
                if (AutoFillField.COLORS in state.touchedFields || state.colorKeys.isNotEmpty()) {
                    state
                } else {
                    state.copy(colorKeys = keys, colorsAutoFilled = true)
                }
            }
        }
    }

    /** 订阅设置：配置/授权变化时重新判断是否该发起（也覆盖「去设置页配好后返回」）。 */
    private fun observeAutoFillSettings() {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                aiConfig = settings.aiConfig
                aiConsent = settings.aiConsent
                evaluateAiStart()
            }
        }
    }

    /**
     * AI 启动闸门（M2-04/M2-06）：
     * 未配置 → 降级引导；未授权 → 弹一次性隐私告知；已拒绝 → 保持降级；已同意且已配置 → 发起。
     */
    private fun evaluateAiStart() {
        if (editingId != null || aiStarted || staged == null) return
        val state = _uiState.value
        // R07：取消是**终止态**。草稿恢复时若曾经「取消识别」，不能因为设置流重新发射
        // 或进程重建又自动发起一次识别（用户已经明确不要这次识别）。
        if (shouldSkipAutoAi(state)) return
        when {
            !aiConfig.isConfigured -> {
                if (state.aiPhase != AiPhase.Degraded || state.aiDegrade != AiDegradeReason.NotConfigured) {
                    _uiState.update {
                        it.copy(aiPhase = AiPhase.Degraded, aiDegrade = AiDegradeReason.NotConfigured)
                    }
                }
            }

            aiConsent == AiConsent.Unasked -> {
                _uiState.update {
                    it.copy(
                        aiPhase = AiPhase.WaitingConsent,
                        showPrivacyDialog = true,
                        privacyEndpointHost = aiConfig.host,
                    )
                }
            }

            aiConsent == AiConsent.Declined -> {
                _uiState.update {
                    it.copy(aiPhase = AiPhase.Degraded, aiDegrade = AiDegradeReason.Declined)
                }
            }

            else -> runRecognition()
        }
    }

    /** 隐私弹窗「同意并继续」：持久化授权后立即发起识别。 */
    fun acceptPrivacy() {
        viewModelScope.launch {
            settingsStore.setAiConsent(AiConsent.Granted)
            aiConsent = AiConsent.Granted
            _uiState.update { it.copy(showPrivacyDialog = false, privacyEndpointHost = null) }
            runRecognition()
        }
    }

    /** 隐私弹窗「暂不使用」：本次起跳过，可随时回设置页开启（M2-06）。 */
    fun declinePrivacy() {
        viewModelScope.launch {
            settingsStore.setAiConsent(AiConsent.Declined)
            aiConsent = AiConsent.Declined
            _uiState.update {
                it.copy(
                    showPrivacyDialog = false,
                    privacyEndpointHost = null,
                    aiPhase = AiPhase.Degraded,
                    aiDegrade = AiDegradeReason.Declined,
                )
            }
        }
    }

    /** 取消识别（M2-04 字段区「取消识别」）：静默降级，不阻断保存。 */
    fun cancelRecognition() {
        aiJob?.cancel()
        _uiState.update {
            it.copy(aiPhase = AiPhase.Degraded, aiDegrade = AiDegradeReason.Cancelled)
        }
    }

    /** 发起识别（15s 超时在 recognizer 内部；协程取消随页面销毁）。 */
    private fun runRecognition() {
        if (aiStarted || editingId != null) return
        val image = staged?.file ?: return
        val endpoint = RecognitionEndpoint(
            baseUrl = aiConfig.baseUrl,
            apiKey = aiConfig.apiKey,
            model = aiConfig.model,
        )
        if (!endpoint.isConfigured) return
        aiStarted = true
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiPhase = AiPhase.Running,
                    aiDegrade = null,
                    showPrivacyDialog = false,
                    privacyEndpointHost = null,
                )
            }
            try {
                val bytes = bitmapLoader.jpegBytes(image, AI_IMAGE_MAX_EDGE)
                val result = recognizer.recognize(endpoint, bytes)
                applySuggestions(result, onlyEmpty = true)
                _uiState.update {
                    it.copy(
                        aiPhase = AiPhase.Prefilled,
                        aiSuggestions = AiSuggestions.from(result),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // 静默降级（§2.2.3）：不弹错、不阻断保存
                android.util.Log.w(LOG_TAG, "AI 识别降级：${failure.javaClass.simpleName}: ${failure.message}")
                _uiState.update {
                    it.copy(aiPhase = AiPhase.Degraded, aiDegrade = AiDegradeReason.Failed)
                }
            }
        }
    }

    /**
     * 「全部采纳」：把 AI 结果填入**尚未手填**的字段。
     *
     * 自动到达时用 `onlyEmpty = true`（只填空字段，绝不覆盖任何已有内容）；
     * 用户点按钮时用 `onlyEmpty = false`（未手填的字段统一采用 AI 值，含清空后重填）。
     * 两条路径都以 `touchedFields` 为唯一手填判据（M2-04 验收）。
     */
    fun acceptAll() {
        val suggestions = _uiState.value.aiSuggestions ?: return
        applySuggestions(suggestions.toRecognition(), onlyEmpty = false)
    }

    private fun applySuggestions(result: AiRecognition, onlyEmpty: Boolean) {
        _uiState.update { state -> applyAiSuggestions(state, result, onlyEmpty) }
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
                    // 先置「草稿已清」再发最后一次状态：否则 collector 可能在 remove 之后
                    // 又把 saving=false 的快照写回来（导航离开后复活草稿）
                    clearDraft()
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
        // R02：最后一道防线——canSave 已要求原图就绪，这里再校验一次，
        // 宁可报错重试，也不允许写入一条没有照片的衣物。
        val original = staged?.takeIf { it.file.exists() }
            ?: throw ImageProcessException("原图尚未就绪，请稍后重试或返回重新选图")
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
            original.let { image ->
                val path = imageStore.promote(image, imageStorage.clothingOriginalFile(id))
                clothingRepository.getItem(id)?.let { item ->
                    clothingRepository.updateItem(item.copy(originalImagePath = path))
                }
            }
        } catch (failure: Throwable) {
            runCatching {
                // 先把已归位的原图退回暂存区（R-14）：promote 会 consume 暂存源，
                // 只删归位产物的话草稿指向的文件已经不存在，用户按提示重试仍然失败
                imageStore.restoreStaged(original, imageStorage.clothingOriginalFile(id))
                // 再回滚数据库：删行 + **撤回本次预约的编号占用**。
                // 不撤回的话，同一个草稿重试会被「编号 500 已被使用」拦住——
                // 用户什么都没得到，号却永久归给了刚被删掉的那一行（R-14 场景 A）。
                clothingRepository.rollbackFailedCreate(id, code)
            }.onFailure { rollbackFailure ->
                android.util.Log.e(LOG_TAG, "建档回滚失败，残留无图行 id=$id", rollbackFailure)
            }
            throw failure
        }
        // M2-01：抠图 PNG 归位。抠图是**增强**而非必需品——归位失败只记日志、
        // 降级为「无抠图」，不能把已经保存成功的衣物报成失败（C-01 兜底口径）。
        stagedCutout?.let { cutout ->
            runCatching {
                val path = imageStore.promote(cutout, imageStorage.clothingCutoutFile(id))
                clothingRepository.getItem(id)?.let { item ->
                    clothingRepository.updateItem(item.copy(cutoutImagePath = path))
                }
            }.onSuccess {
                stagedCutout = null
            }.onFailure { failure ->
                android.util.Log.w(LOG_TAG, "抠图归位失败，已降级为无抠图", failure)
                runCatching { imageStorage.clothingCutoutFile(id).delete() }
            }
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
        clearDraft()
        releaseStagedResources()
    }

    /**
     * R08：**未保存就离开**（直接返回/系统返回且无改动）时，ViewModel 被清理由这里
     * 删除暂存原图与抠图，不能让 cache 里永远堆着没人引用的图片。
     * 保存成功时文件已被 promote 消费（不存在则 delete 幂等）；进程被杀时 onCleared
     * 不会执行，暂存文件仍随草稿保留——这正是恢复所需。
     */
    private fun releaseStagedResources() {
        stagedResourcesReleased = true
        staged?.file?.delete()
        staged = null
        stagedCutout?.file?.delete()
        stagedCutout = null
    }

    override fun onCleared() {
        aiJob?.cancel()
        colorJob?.cancel()
        if (!draftCleared) {
            // 正常离开（未保存）不应复活草稿；只有进程回收才会跳过 onCleared
            savedStateHandle.remove<String>(ClothingFormDraft.KEY)
        }
        if (!stagedResourcesReleased) releaseStagedResources()
    }

    /** 保存/放弃后清草稿：进程再被回收时不应复活已结束的编辑 */
    private fun clearDraft() {
        draftCleared = true
        savedStateHandle.remove<String>(ClothingFormDraft.KEY)
    }

    private companion object {
        const val LOG_TAG = "ClothingForm"

        /** 送给 AI 的图片长边（1024：细节够识别、base64 体积可控） */
        const val AI_IMAGE_MAX_EDGE = 1024
    }
}

/** 衣物表单 UI 状态（创建 + 编辑共用） */
data class ClothingFormUiState(
    val isEdit: Boolean = false,
    /** 展示用照片路径：创建模式 = 暂存图绝对路径；编辑模式 = 既有相对路径 */
    val imagePath: String? = null,
    /** 当前展示的是抠图 PNG（透明底）→ 用 Fit 而非 Crop，避免裁掉主体 */
    val imageIsCutout: Boolean = false,
    val compressing: Boolean = false,
    /** R02：创建模式必须有有效的暂存原图才允许保存；编辑模式为只读照片 */
    val imageReady: Boolean = false,
    /** R02：暂存原图失效且无法自动重取，UI 显示重试/重新选图入口 */
    val imageUnavailable: Boolean = false,
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

    // ── M2-04 AI 异步预填 ───────────────────────────────────
    val aiPhase: AiPhase = AiPhase.Idle,
    val aiSuggestions: AiSuggestions? = null,
    val aiDegrade: AiDegradeReason? = null,
    /** 一次性隐私告知弹窗是否显示（M2-06） */
    val showPrivacyDialog: Boolean = false,
    /** 弹窗里展示的端点主机名（不含 Key 与路径） */
    val privacyEndpointHost: String? = null,
    /** 该字段当前值来自 AI（✨ 角标）；用户改动后立即清掉 */
    val categoryAiFilled: Boolean = false,
    val styleAiFilled: Boolean = false,
    val seasonAiFilled: Boolean = false,
    /** 颜色来自本地提取（"本地提取"角标，不是 AI） */
    val colorsAutoFilled: Boolean = false,
    /** 用户手填过的字段（M2-04「手填不被覆盖」的唯一判据） */
    val touchedFields: Set<AutoFillField> = emptySet(),
) {
    /** 可保存：编号合法 + 已选衣柜 + 不在压缩/保存中。保存按钮据此禁用（§6.3 38% 透明）。 */
    val canSave: Boolean
        get() = !compressing && !saving && !notFound &&
            (isEdit || imageReady) &&
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


/**
 * AI 异步预填的页面状态（M2-04 / UI 文档 §7.3 三态）。
 *
 * - [Idle]：编辑模式或尚未开始；
 * - [WaitingConsent]：已配置但还没授权 → 弹一次性隐私告知（M2-06）；
 * - [Running]：识别中（可取消）；
 * - [Prefilled]：已预填（字段带 ✨，可逐项改）；
 * - [Degraded]：未配置/拒绝/失败/取消 → 字段留空手填，保存不阻断。
 */
enum class AiPhase {
    Idle,
    WaitingConsent,
    Running,
    Prefilled,
    Degraded,
}

/** 降级原因（UI 据此决定显示引导入口还是静默提示）。 */
enum class AiDegradeReason {
    NotConfigured,
    Declined,
    Failed,
    Cancelled,
}

/** 用户手填过的字段（AI「全部采纳」只填不在集合里的字段）。 */
enum class AutoFillField {
    CATEGORY,
    COLORS,
    STYLE,
    SEASON,
    BRAND,
    NOTE,
    CODE,
    NAME,
    WARDROBE,
}

/** AI 建议的可持久化快照（草稿抗进程重建时也要保住 ✨ 角标）。 */
@Serializable
data class AiSuggestions(
    val categoryKey: String? = null,
    val styleTags: List<String> = emptyList(),
    val seasonKeys: List<String> = emptyList(),
) {
    fun toRecognition(): AiRecognition = AiRecognition(
        categoryKey = categoryKey,
        styleTags = styleTags,
        seasonKeys = seasonKeys,
    )

    companion object {
        fun from(result: AiRecognition): AiSuggestions = AiSuggestions(
            categoryKey = result.categoryKey,
            styleTags = result.styleTags,
            seasonKeys = result.seasonKeys,
        )
    }
}


/**
 * AI 建议 → 表单状态 —— **纯函数**（M2-04「手填不被覆盖」的直测载体）。
 *
 * 判据只有 `touchedFields`：
 * - `onlyEmpty = true`（自动预填）：只填空字段；
 * - `onlyEmpty = false`（全部采纳）：未手填的字段统一采用 AI 值。
 * 手填过的字段在两种路径下都不会被改写。
 */
/**
 * R07：AI 自动启动的终止态判定 —— 纯函数，JVM 直测。
 *
 * - Running：已在跑，不能重复发起；
 * - Prefilled / 有建议：已有结果，重跑会覆盖恢复态并重复计费；
 * - Degraded(Cancelled)：用户明确取消，必须保持终止，不能在设置流重发/进程恢复后复活。
 */
/**
 * U03：追加自定义风格标签，并清除 AI 来源角标（纯函数，JVM 直测）。
 * 同名标签也算用户已确认：标签保留，但不再标成 AI。
 */
internal fun ClothingFormUiState.withCustomStyleTag(tag: String): ClothingFormUiState =
    if (tag in styleTags) {
        copy(styleAiFilled = false)
    } else {
        copy(styleTags = styleTags + tag, styleAiFilled = false)
    }

internal fun shouldSkipAutoAi(state: ClothingFormUiState): Boolean =
    state.aiPhase == AiPhase.Running ||
        state.aiPhase == AiPhase.Prefilled ||
        state.aiSuggestions != null ||
        state.aiDegrade == AiDegradeReason.Cancelled

internal fun applyAiSuggestions(
    state: ClothingFormUiState,
    result: AiRecognition,
    onlyEmpty: Boolean,
): ClothingFormUiState {
    val touched = state.touchedFields
    var next = state.copy(aiSuggestions = AiSuggestions.from(result))

    val category = result.categoryKey
    if (category != null &&
        AutoFillField.CATEGORY !in touched &&
        (!onlyEmpty || state.categoryKey == null)
    ) {
        next = next.copy(
            categoryKey = category,
            expandedTopKey = ClothingCategory.fromKey(category)?.topKey,
            categoryAiFilled = true,
        )
    }
    if (result.styleTags.isNotEmpty() &&
        AutoFillField.STYLE !in touched &&
        (!onlyEmpty || state.styleTags.isEmpty())
    ) {
        next = next.copy(styleTags = result.styleTags, styleAiFilled = true)
    }
    if (result.seasonKeys.isNotEmpty() &&
        AutoFillField.SEASON !in touched &&
        (!onlyEmpty || state.seasonKeys.isEmpty())
    ) {
        next = next.copy(seasonKeys = result.seasonKeys, seasonAiFilled = true)
    }
    return next
}
