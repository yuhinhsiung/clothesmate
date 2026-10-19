package com.yuhinhsiung.clothesmate.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {

    @Test
    fun `deepseek official url variants map to preset`() {
        assertEquals(
            AiProvider.DeepSeek,
            AiProvider.fromBaseUrl("https://api.deepseek.com"),
        )
        assertEquals(
            AiProvider.DeepSeek,
            AiProvider.fromBaseUrl(" https://api.deepseek.com/ "),
        )
        assertEquals(
            AiProvider.DeepSeek,
            AiProvider.fromBaseUrl("https://api.deepseek.com/v1/"),
        )
    }

    @Test
    fun `other compatible endpoints stay custom`() {
        assertEquals(AiProvider.Custom, AiProvider.fromBaseUrl("https://api.openai.com/v1"))
        assertEquals(AiProvider.Custom, AiProvider.fromBaseUrl("http://10.0.2.2:8080/v1"))
        assertEquals(AiProvider.Custom, AiProvider.fromBaseUrl(""))
    }

    @Test
    fun `deepseek preset carries vision capable defaults`() {
        assertTrue(AiProvider.DEEPSEEK_BASE_URL.startsWith("https://"))
        assertEquals("deepseek-flash", AiProvider.DEEPSEEK_MODEL)
        assertFalse(AiProvider.DEEPSEEK_MODEL.isBlank())
    }

    @Test
    fun `custom default model is non blank`() {
        assertTrue(AiProvider.CUSTOM_DEFAULT_MODEL.isNotBlank())
    }
}
