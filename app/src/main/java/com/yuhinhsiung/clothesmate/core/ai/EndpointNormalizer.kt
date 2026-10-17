package com.yuhinhsiung.clothesmate.core.ai

import java.net.URI

/**
 * Base URL 归一化 —— M2-03 验收项「用户可能不带 `/v1`」。
 *
 * 用户会在设置页填各种形态：
 *
 * | 输入 | 归一结果 |
 * |---|---|
 * | `https://api.openai.com` | `https://api.openai.com/v1/chat/completions` |
 * | `https://api.openai.com/v1/` | `https://api.openai.com/v1/chat/completions` |
 * | `https://api.deepseek.com/v1` | `https://api.deepseek.com/v1/chat/completions` |
 * | `https://open.bigmodel.cn/api/paas/v4` | `.../api/paas/v4/chat/completions` |
 * | `https://api.openai.com/v1/chat/completions` | 原样 |
 * | `api.deepseek.com/v1`（无 scheme） | 补 `https://` |
 *
 * 只在**路径为空**时补 `/v1`：有路径的端点（DeepSeek 的 `/v1`、GLM 的 `/api/paas/v4`、
 * Ollama 的 `/v1`）必须原样尊重，不能替用户猜版本段。
 */
object EndpointNormalizer {

    private const val CHAT_SUFFIX = "/chat/completions"

    private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /** @return 完整的 `.../chat/completions` URL；无法解析时 null（设置页据此拦住测试连接） */
    fun chatCompletionsUrl(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) return null
        // 已带任意 scheme（含 ftp:// 等）就原样解析，交给下面的 http/https 校验拒绝；
        // 只有「完全没写 scheme」时才补 https://（用户常见的 api.xxx.com/v1）
        val hasScheme = SCHEME_PATTERN.containsMatchIn(raw)
        val withScheme = if (hasScheme) raw else "https://$raw"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.query != null || uri.fragment != null) return null

        val path = uri.path.orEmpty().trimEnd('/')
        if (path.endsWith(CHAT_SUFFIX)) {
            return URI(uri.scheme, uri.userInfo, uri.host, uri.port, path, null, null).toString()
        }
        val basePath = if (path.isEmpty()) "/v1" else path
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, basePath, null, null).toString()
            .trimEnd('/') + CHAT_SUFFIX
    }

    /** 设置页状态文案用的主机名（不含路径/凭据） */
    fun hostOf(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) return null
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            "https://$raw"
        }
        return runCatching { URI(withScheme).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
