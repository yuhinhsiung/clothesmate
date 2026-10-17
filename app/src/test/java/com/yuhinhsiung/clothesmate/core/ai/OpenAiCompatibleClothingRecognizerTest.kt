package com.yuhinhsiung.clothesmate.core.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容识别封装（M2-03）—— MockWebServer 直测 HTTP 契约与降级。
 *
 * 覆盖验收要求的四类：正常 JSON 解析、非 JSON / 空 content 容错、超时取消、
 * HTTP 4xx/5xx 降级；并顺带锁定「不内置 Key」（Authorization 来自入参）。
 */
class OpenAiCompatibleClothingRecognizerTest {

    private lateinit var server: MockWebServer
    private lateinit var recognizer: OpenAiCompatibleClothingRecognizer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        recognizer = OpenAiCompatibleClothingRecognizer(api(), timeoutMillis = 2_000)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(): ChatCompletionsApi {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChatCompletionsApi::class.java)
    }

    private fun endpoint() = RecognitionEndpoint(
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        apiKey = "sk-test-key",
        model = "gpt-4o-mini",
    )

    private fun chatResponse(content: String): String =
        """{"choices":[{"message":{"role":"assistant","content":${jsonString(content)}}}]}"""

    private fun jsonString(raw: String): String = kotlinx.serialization.json.JsonPrimitive(raw).toString()

    @Test
    fun `recognizes a normal json response and sends the expected request`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                chatResponse("""{"category":"shirt","styleTags":["通勤"],"seasonTags":["spring"]}"""),
            ),
        )

        val result = recognizer.recognize(endpoint(), byteArrayOf(1, 2, 3, 4))

        assertEquals("shirt", result.categoryKey)
        assertEquals(listOf("通勤"), result.styleTags)
        assertEquals(listOf("spring"), result.seasonKeys)

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer sk-test-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\""))
        assertTrue(body.contains("data:image/jpeg;base64,"))
        assertFalse(body.contains("sk-test-key"))
    }

    @Test
    fun `empty content degrades with a recognition exception`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatResponse("")))
        val failure = runCatching { recognizer.recognize(endpoint(), byteArrayOf(1)) }.exceptionOrNull()
        assertTrue(failure is AiRecognitionException)
    }

    @Test
    fun `non json content degrades`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatResponse("我看不清这张图")))
        val failure = runCatching { recognizer.recognize(endpoint(), byteArrayOf(1)) }.exceptionOrNull()
        assertTrue(failure is AiRecognitionException)
    }

    @Test
    fun `http 500 degrades without leaking key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        val failure = runCatching { recognizer.recognize(endpoint(), byteArrayOf(1)) }.exceptionOrNull()
        assertTrue(failure is AiRecognitionException)
        assertFalse(failure!!.message.orEmpty().contains("sk-test-key"))
    }

    @Test
    fun `slow response hits the timeout`() = runBlocking {
        val slow = OpenAiCompatibleClothingRecognizer(api(), timeoutMillis = 200)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(chatResponse("""{"category":"shirt"}"""))
                .setBodyDelay(3, TimeUnit.SECONDS),
        )
        val failure = runCatching { slow.recognize(endpoint(), byteArrayOf(1)) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure is AiRecognitionException)
    }

    @Test
    fun `test connection reports success and failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"pong"}}]}"""))
        val ok = recognizer.testConnection(endpoint())
        assertTrue(ok.success)

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        val bad = recognizer.testConnection(endpoint())
        assertFalse(bad.success)
        assertEquals("HTTP 401", bad.detail)
    }

    @Test
    fun `incomplete config is rejected before any request`() = runBlocking {
        val failure = runCatching {
            recognizer.recognize(RecognitionEndpoint("", "", ""), byteArrayOf(1))
        }.exceptionOrNull()
        assertTrue(failure is AiRecognitionException)
        assertEquals(0, server.requestCount)
    }
}
