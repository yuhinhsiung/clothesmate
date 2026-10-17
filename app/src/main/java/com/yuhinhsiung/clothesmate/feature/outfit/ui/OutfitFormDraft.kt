package com.yuhinhsiung.clothesmate.feature.outfit.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 穿搭表单草稿 —— M2-08 / review B-18（与衣物表单同一套「SavedStateHandle 草稿」口径）。
 *
 * 照片分两段保存：既有照片（编辑模式，相对路径）+ 本次新加的暂存照片（绝对路径）。
 * 暂存文件在进程死亡后仍在 cache 里；系统清缓存后恢复时只丢掉那一张，其余字段照常恢复。
 */
@Serializable
data class OutfitFormDraft(
    val isEdit: Boolean = false,
    val existingPaths: List<String> = emptyList(),
    val stagedPhotoPaths: List<String> = emptyList(),
    val removedExistingPaths: List<String> = emptyList(),
    val selectedItemIds: List<Long> = emptyList(),
    val selectedCategoryIds: List<Long> = emptyList(),
    val styleTags: List<String> = emptyList(),
    val sceneTags: List<String> = emptyList(),
    val seasonTags: List<String> = emptyList(),
    val note: String = "",
    val baseline: OutfitFormBaseline = OutfitFormBaseline(),
) {
    fun toUiState(base: OutfitFormUiState, stagedPaths: List<String>): OutfitFormUiState =
        base.copy(
            isEdit = isEdit,
            existingPaths = existingPaths,
            stagedDisplayPaths = stagedPaths,
            removedExistingPaths = removedExistingPaths,
            selectedItemIds = selectedItemIds,
            selectedCategoryIds = selectedCategoryIds.toSet(),
            styleTags = styleTags,
            sceneTags = sceneTags,
            seasonTags = seasonTags,
            note = note,
            baseline = baseline,
            addingPhoto = false,
        )

    companion object {
        const val KEY = "outfit_form_draft_v1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(state: OutfitFormUiState, stagedPhotoPaths: List<String>): String =
            json.encodeToString(
                OutfitFormDraft(
                    isEdit = state.isEdit,
                    existingPaths = state.existingPaths,
                    stagedPhotoPaths = stagedPhotoPaths,
                    removedExistingPaths = state.removedExistingPaths,
                    selectedItemIds = state.selectedItemIds,
                    selectedCategoryIds = state.selectedCategoryIds.toList(),
                    styleTags = state.styleTags,
                    sceneTags = state.sceneTags,
                    seasonTags = state.seasonTags,
                    note = state.note,
                    baseline = state.baseline,
                ),
            )

        fun decode(raw: String?): OutfitFormDraft? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<OutfitFormDraft>(raw) }.getOrNull()
        }
    }
}
