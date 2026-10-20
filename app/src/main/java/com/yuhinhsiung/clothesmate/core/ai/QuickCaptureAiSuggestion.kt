package com.yuhinhsiung.clothesmate.core.ai

import kotlinx.serialization.Serializable

/**
 * 连拍后台识别的**待确认建议**，不是衣物字段（review R10）。
 * 用户在统一确认页点「采纳」后，才会以字段级补丁写入衣物。
 */
@Serializable
data class QuickCaptureAiSuggestion(
    val categoryKey: String? = null,
    val styleTags: List<String> = emptyList(),
    val seasonTags: List<String> = emptyList(),
    val createdAt: Long,
) {
    val isEmpty: Boolean
        get() = categoryKey == null && styleTags.isEmpty() && seasonTags.isEmpty()
}
