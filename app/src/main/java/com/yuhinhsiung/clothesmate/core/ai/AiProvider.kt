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

        /** 首版预设误用的旧模型名；仅用于升级迁移，不再允许作为预设生效。 */
        const val LEGACY_DEEPSEEK_MODEL = "deepseek-v4-flash-vision-exp"

        const val CUSTOM_DEFAULT_MODEL = "gpt-4o-mini"

        /** 根据当前 Base URL 识别预设；DeepSeek 官方带不带 /v1 都算预设。 */
        /**
         * 界面只在「URL + 固定模型」同时成立时展示 DeepSeek 预设。
         *
         * 旧配置若保存了官方 URL 但模型不是预设模型，必须回落 Custom，让模型框
         * 可见；否则会隐藏仍在生效的真实模型（review N01）。
         */
        fun fromConfig(rawBaseUrl: String, rawModel: String): AiProvider =
            if (fromBaseUrl(rawBaseUrl) == DeepSeek && rawModel.trim() == DEEPSEEK_MODEL) {
                DeepSeek
            } else {
                Custom
            }

        fun isLegacyDeepSeekModel(raw: String): Boolean =
            raw.trim() == LEGACY_DEEPSEEK_MODEL

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
