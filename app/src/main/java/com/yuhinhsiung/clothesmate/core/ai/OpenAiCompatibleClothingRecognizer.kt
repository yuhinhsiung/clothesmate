package com.yuhinhsiung.clothesmate.core.ai

import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容识别实现 —— M2-03 / 开发文档 §2.2.3 C-02。
 *
 * ## 请求形态
 *
 * `POST {base}/chat/completions`，messages 内以 `image_url` + base64 data URL 携带图片。
 * 提示词把输出约束成 JSON（类别 key / 中文风格标签 / 季节 key），解析交给
 * [RecognitionParser]——HTTP 与解析分开，后者可 JVM 直测。
 *
 * ## 兼容性取舍
 *
 * - **不传 `response_format`**：OpenAI / DeepSeek 支持，但大量兼容端点（尤其本地 Ollama、
 *   部分国产网关）会 400；靠提示词约束 + 宽容解析更稳。
 * - **15s 超时**：OkHttp 的 callTimeout 与协程 `withTimeout` 双保险（文档 §2.2.1）。
 * - **不记录请求体**：body 含 base64 照片；日志里只允许出现端点主机名与异常类型。
 */
@Singleton
class OpenAiCompatibleClothingRecognizer @Inject constructor(
    private val api: ChatCompletionsApi,
) : ClothingRecognizer {

    /** 测试可注入更短超时（M2-03 超时用例）；生产走 [DEFAULT_TIMEOUT_MILLIS] */
    private var timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS

    internal constructor(api: ChatCompletionsApi, timeoutMillis: Long) : this(api) {
        this.timeoutMillis = timeoutMillis
    }

    override suspend fun recognize(
        endpoint: RecognitionEndpoint,
        imageJpeg: ByteArray,
    ): AiRecognition {
        if (!endpoint.isConfigured) {
            throw AiRecognitionException("AI 端点未配置")
        }
        val url = EndpointNormalizer.chatCompletionsUrl(endpoint.baseUrl)
            ?: throw AiRecognitionException("Base URL 无效")
        val base64 = Base64.getEncoder().encodeToString(imageJpeg)
        val body = buildJsonObject {
            put("model", endpoint.model.trim())
            put("temperature", 0.1)
            put("max_tokens", MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", USER_PROMPT)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64")
                            }
                        }
                    }
                }
            }
        }
        val response = try {
            call(url, endpoint.apiKey, body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (already: AiRecognitionException) {
            throw already
        } catch (failure: Throwable) {
            throw AiRecognitionException(describeFailure(failure), failure)
        }
        val content = RecognitionParser.extractContent(response)
            ?: throw AiRecognitionException("AI 返回内容为空")
        return RecognitionParser.parse(content)
            ?: throw AiRecognitionException("AI 返回内容无法解析为衣物信息")
    }

    override suspend fun testConnection(endpoint: RecognitionEndpoint): ConnectionTestResult {
        if (!endpoint.isConfigured) return ConnectionTestResult(false, "配置不完整")
        val url = EndpointNormalizer.chatCompletionsUrl(endpoint.baseUrl)
            ?: return ConnectionTestResult(false, "Base URL 无效")
        val body = buildJsonObject {
            put("model", endpoint.model.trim())
            put("max_tokens", 1)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "ping")
                }
            }
        }
        return try {
            val response = call(url, endpoint.apiKey, body)
            val choices = response["choices"]
            if (choices != null) {
                ConnectionTestResult(true)
            } else {
                ConnectionTestResult(false, response["error"]?.toString()?.take(120) ?: "响应缺少 choices")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val detail = if (failure is AiRecognitionException) {
                failure.message ?: describeFailure(failure)
            } else {
                describeFailure(failure)
            }
            ConnectionTestResult(false, detail)
        }
    }

    private suspend fun call(
        url: String,
        apiKey: String,
        body: JsonObject,
    ): JsonObject = try {
        withTimeout(timeoutMillis) {
            api.completions(url, "Bearer ${apiKey.trim()}", body)
        }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        // 协程超时也是 CancellationException，但语义上是「本次识别失败」，
        // 必须转成 AiRecognitionException 才能走降级；外部取消继续向上传播。
        throw AiRecognitionException("请求超时", timeout)
    }

    /** 只描述失败类型/状态码，**绝不**带出 Key 或请求体。 */
    private fun describeFailure(failure: Throwable): String = when (failure) {
        is HttpException -> "HTTP ${failure.code()}"
        is java.net.SocketTimeoutException -> "请求超时"
        is java.net.UnknownHostException -> "域名无法解析"
        is IOException -> "网络不可用"
        is kotlinx.coroutines.TimeoutCancellationException -> "请求超时"
        else -> failure.javaClass.simpleName
    }

    private companion object {
        /** 单次识别 15s（开发文档 §2.2.1） */
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val MAX_TOKENS = 300

        /** 注意：提示词只约束「输出 JSON」，不承诺模型一定照做——解析层做宽容兜底。 */
        val SYSTEM_PROMPT = """
            你是一名衣物识别助手。请只输出一个 JSON 对象，不要输出 Markdown、解释或多余文字。
            JSON 字段：
            - "category": 必须是以下 key 之一：t_shirt, shirt, hoodie, sweater, top_other,
              jacket, coat, down, outer_other, shorts, pants, skirt_short, skirt_long,
              bottom_other, dress, sneakers, loafers, boots, sandals, shoes_other,
              handbag, shoulder, backpack, bag_other, hat, scarf, belt, acc_other
            - "styleTags": 0 到 3 个中文风格标签，从「休闲、通勤、运动、甜美、复古、街头、正式、简约」中选
            - "seasonTags": 0 到 4 个，取值只能是 spring, summer, autumn, winter
            示例：{"category":"shirt","styleTags":["通勤","简约"],"seasonTags":["spring","autumn"]}
        """.trimIndent()

        val USER_PROMPT = "请识别这件衣物的类别、风格标签和适合季节。只返回 JSON。"
    }
}
