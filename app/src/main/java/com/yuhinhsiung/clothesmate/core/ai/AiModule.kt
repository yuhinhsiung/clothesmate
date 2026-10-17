package com.yuhinhsiung.clothesmate.core.ai

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * AI 网络层装配 —— M2-03。
 *
 * 只装配「OpenAI 兼容 Chat Completions」这一条通道（开发文档 §2.2.3 决策）：
 * - 无内置 Key / 无厂商 SDK，端点三件套来自设置页；
 * - **不加 OkHttp 日志拦截器**：请求体是 base64 照片、头里有 Key，日志会泄漏隐私。
 *   需要排错时用 `adb logcat` 里我们自己的、已脱敏的失败类型。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiProvidesModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 总时长兜底：协程侧还有 withTimeout(15s)，双保险
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit = Retrofit.Builder()
        // @Url 会覆盖这里的 baseUrl；只为满足 Retrofit 的构造要求
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatCompletionsApi =
        retrofit.create(ChatCompletionsApi::class.java)

    private const val TIMEOUT_SECONDS = 15L
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindModule {

    @Binds
    @Singleton
    abstract fun bindClothingRecognizer(
        impl: OpenAiCompatibleClothingRecognizer,
    ): ClothingRecognizer
}
