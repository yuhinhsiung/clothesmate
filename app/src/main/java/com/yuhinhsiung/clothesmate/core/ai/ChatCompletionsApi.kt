package com.yuhinhsiung.clothesmate.core.ai

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 兼容 Chat Completions 的最小 Retrofit 契约（开发文档 §2.2.3 / §8）。
 *
 * - `@Url` 接受完整 URL：Base URL 由用户配置，不能在 Retrofit 构造时固定；
 * - 请求/响应都用 [JsonObject]，避免把各家兼容端点的额外字段写死成 DTO
 *   （`ignoreUnknownKeys` 也救不了「字段类型不一致」的端点）；
 * - 认证固定 `Authorization: Bearer <apiKey>`（OpenAI 兼容的事实标准）。
 */
interface ChatCompletionsApi {

    @Headers("Accept: application/json")
    @POST
    suspend fun completions(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): JsonObject
}
