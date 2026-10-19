package com.yuhinhsiung.clothesmate.core.ai

/**
 * AI 端点预设。
 *
 * DeepSeek 使用官方 OpenAI 兼容端点与支持图片输入的 deepseek-flash，用户只需填 API Key；
 * 自定义端点仍允许任意 OpenAI 兼容 URL，模型名有默认值、可改。
 */
enum class AiProvider {
    DeepSeek,
    Custom;

    companion object {
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val DEEPSEEK_MODEL = "deepseek-flash"
        const val CUSTOM_DEFAULT_MODEL = "gpt-4o-mini"

        /** 根据当前 Base URL 识别预设；DeepSeek 官方带不带 /v1 都算预设。 */
        fun fromBaseUrl(raw: String): AiProvider {
            val normalized = raw.trim()
                .trimEnd('/')
                .lowercase()
            return if (
                normalized == DEEPSEEK_BASE_URL ||
                normalized == "$DEEPSEEK_BASE_URL/v1"
            ) {
                DeepSeek
            } else {
                Custom
            }
        }
    }
}
