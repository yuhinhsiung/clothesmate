package com.yuhinhsiung.clothesmate.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Base URL 归一化（M2-03 验收「用户可能不带 /v1」）。 */
class EndpointNormalizerTest {

    @Test
    fun `host only gets v1 and chat completions`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            EndpointNormalizer.chatCompletionsUrl("https://api.openai.com"),
        )
    }

    @Test
    fun `trailing slash is trimmed`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            EndpointNormalizer.chatCompletionsUrl("https://api.openai.com/v1/"),
        )
    }

    @Test
    fun `existing path is respected`() {
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            EndpointNormalizer.chatCompletionsUrl("https://open.bigmodel.cn/api/paas/v4"),
        )
    }

    @Test
    fun `full endpoint is returned unchanged`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            EndpointNormalizer.chatCompletionsUrl("https://api.deepseek.com/v1/chat/completions"),
        )
    }

    @Test
    fun `missing scheme gets https`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            EndpointNormalizer.chatCompletionsUrl("api.deepseek.com/v1"),
        )
    }

    @Test
    fun `local port is preserved`() {
        assertEquals(
            "http://10.0.2.2:11434/v1/chat/completions",
            EndpointNormalizer.chatCompletionsUrl("http://10.0.2.2:11434/v1"),
        )
    }

    @Test
    fun `invalid input returns null`() {
        assertNull(EndpointNormalizer.chatCompletionsUrl(""))
        assertNull(EndpointNormalizer.chatCompletionsUrl("   "))
        assertNull(EndpointNormalizer.chatCompletionsUrl("ftp://example.com/v1"))
        assertNull(EndpointNormalizer.chatCompletionsUrl("https://example.com/v1?x=1"))
    }

    @Test
    fun `host hides path and credentials`() {
        assertEquals("api.deepseek.com", EndpointNormalizer.hostOf("https://api.deepseek.com/v1"))
    }
}
