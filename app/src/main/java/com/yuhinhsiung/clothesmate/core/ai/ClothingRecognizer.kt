package com.yuhinhsiung.clothesmate.core.ai

/**
 * AI 识别能力接口 —— 开发文档 §2.2.3 C-02、§4.3「AI 能力接口化」。
 *
 * 默认实现是 OpenAI 兼容 Chat Completions（[OpenAiCompatibleClothingRecognizer]），
 * 未来可替换为其他供应商；调用方（确认页）只依赖本接口，不知道 HTTP 细节。
 *
 * ## 红线
 *
 * - **不内置 Key、不锁定厂商**：端点三件套由调用方从设置页读取后传入；
 * - 任何失败（未配置/超时/网络/非 JSON/空 content）都抛 [AiRecognitionException]，
 *   由调用方静默降级为「字段留空手填」，**不阻断保存**；
 * - 识别结果只作预填，写入 UI 前必须带 ✨ 标识（R-4）。
 */
interface ClothingRecognizer {

    /**
     * 识别单件衣物照片。
     *
     * @param endpoint 用户配置的端点（Base URL / Key / 模型名）
     * @param imageJpeg 已缩放到合理尺寸的 JPEG 字节（不含 base64 编码）
     * @return 解析后的类别 / 风格 / 季节建议
     * @throws AiRecognitionException 超时、HTTP 非 2xx、响应不可解析、字段全空
     */
    suspend fun recognize(endpoint: RecognitionEndpoint, imageJpeg: ByteArray): AiRecognition

    /**
     * 设置页「测试连接」：发一次极小请求验证端点可用。
     *
     * 失败不抛异常，而是用 [ConnectionTestResult] 表达，便于 UI 显示红点与原因。
     */
    suspend fun testConnection(endpoint: RecognitionEndpoint): ConnectionTestResult
}

/**
 * 识别端点三件套。
 *
 * 刻意不复用 DataStore 的 `AiConfig`：core/ai 不应依赖偏好存储，测试可以直传假端点。
 */
data class RecognitionEndpoint(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** AI 识别结果（只含可预填的三个字段，颜色由本地提取负责）。 */
data class AiRecognition(
    val categoryKey: String? = null,
    val styleTags: List<String> = emptyList(),
    val seasonKeys: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = categoryKey == null && styleTags.isEmpty() && seasonKeys.isEmpty()
}

/** 测试连接结果（不抛异常，让设置页能稳定展示状态点）。 */
data class ConnectionTestResult(
    val success: Boolean,
    /** 失败原因（HTTP 401 / 超时 / 响应格式不符…），**不得包含 Key** */
    val detail: String? = null,
)

/** AI 识别失败（统一异常类型，调用方只处理这一种）。 */
class AiRecognitionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
