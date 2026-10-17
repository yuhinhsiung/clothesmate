package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 衣物表单草稿 —— M2-08 / review B-18「表单不抗进程重建」的落点。
 *
 * 为什么用 `SavedStateHandle` 而不是 DataStore：草稿的语义是「这次编辑会话」，
 * 进程被系统回收后应随任务栈恢复，用户主动放弃/保存后必须消失；DataStore 会
 * 让它跨会话残留，还得额外做清理与过期策略。
 *
 * 暂存图路径是 `cacheDir` 下的**绝对路径**（仅进程内用，不写库）：进程死亡后缓存
 * 文件仍在，恢复时校验存在性；文件被系统清掉则退回「重新选图」而不是崩溃。
 */
@Serializable
data class ClothingFormDraft(
    val imageStagedPath: String? = null,
    val cutoutStagedPath: String? = null,
    val imagePath: String? = null,
    val imageIsCutout: Boolean = false,
    val name: String = "",
    val categoryKey: String? = null,
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
    val userModified: Boolean = false,
    val touchedFields: List<String> = emptyList(),
    val aiPhase: String = AiPhase.Idle.name,
    val aiSuggestions: AiSuggestions? = null,
    val aiDegrade: String? = null,
    val categoryAiFilled: Boolean = false,
    val styleAiFilled: Boolean = false,
    val seasonAiFilled: Boolean = false,
    val colorsAutoFilled: Boolean = false,
    val privacyEndpointHost: String? = null,
) {
    fun toUiState(base: ClothingFormUiState): ClothingFormUiState {
        val phase = AiPhase.entries.firstOrNull { it.name == aiPhase } ?: AiPhase.Idle
        // 进程死亡时识别任务也死了：Running 恢复成 Idle（有建议就按已预填展示），
        // 不假装还在跑；WaitingConsent 继续弹一次性告知。
        val restoredPhase = when (phase) {
            AiPhase.Running -> if (aiSuggestions != null) AiPhase.Prefilled else AiPhase.Idle
            else -> phase
        }
        return base.copy(
            imagePath = imagePath,
            imageIsCutout = imageIsCutout,
            name = name,
            categoryKey = categoryKey,
            expandedTopKey = expandedTopKey,
            colorKeys = colorKeys,
            styleTags = styleTags,
            seasonKeys = seasonKeys,
            brand = brand,
            note = note,
            wardrobeId = wardrobeId,
            wardrobeName = wardrobeName,
            code = code,
            codeTouched = codeTouched,
            userModified = userModified,
            touchedFields = touchedFields.mapNotNull { name ->
                AutoFillField.entries.firstOrNull { it.name == name }
            }.toSet(),
            aiPhase = restoredPhase,
            aiSuggestions = aiSuggestions,
            aiDegrade = aiDegrade?.let { key ->
                AiDegradeReason.entries.firstOrNull { it.name == key }
            },
            categoryAiFilled = categoryAiFilled,
            styleAiFilled = styleAiFilled,
            seasonAiFilled = seasonAiFilled,
            colorsAutoFilled = colorsAutoFilled,
            showPrivacyDialog = restoredPhase == AiPhase.WaitingConsent,
            privacyEndpointHost = privacyEndpointHost,
        )
    }

    companion object {
        /** SavedStateHandle key（带版本，将来结构变化可整体丢弃） */
        const val KEY = "clothing_form_draft_v1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(
            state: ClothingFormUiState,
            stagedPath: String?,
            cutoutPath: String?,
        ): String = json.encodeToString(
            ClothingFormDraft(
                imageStagedPath = stagedPath,
                cutoutStagedPath = cutoutPath,
                imagePath = state.imagePath,
                imageIsCutout = state.imageIsCutout,
                name = state.name,
                categoryKey = state.categoryKey,
                expandedTopKey = state.expandedTopKey,
                colorKeys = state.colorKeys,
                styleTags = state.styleTags,
                seasonKeys = state.seasonKeys,
                brand = state.brand,
                note = state.note,
                wardrobeId = state.wardrobeId,
                wardrobeName = state.wardrobeName,
                code = state.code,
                codeTouched = state.codeTouched,
                userModified = state.userModified,
                touchedFields = state.touchedFields.map { it.name },
                aiPhase = state.aiPhase.name,
                aiSuggestions = state.aiSuggestions,
                aiDegrade = state.aiDegrade?.name,
                categoryAiFilled = state.categoryAiFilled,
                styleAiFilled = state.styleAiFilled,
                seasonAiFilled = state.seasonAiFilled,
                colorsAutoFilled = state.colorsAutoFilled,
                privacyEndpointHost = state.privacyEndpointHost,
            ),
        )

        fun decode(raw: String?): ClothingFormDraft? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<ClothingFormDraft>(raw) }.getOrNull()
        }
    }
}
