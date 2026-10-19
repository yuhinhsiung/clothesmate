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

    @Test
    fun `provider requires both official url and fixed model`() {
        assertEquals(
            AiProvider.DeepSeek,
            AiProvider.fromConfig(AiProvider.DEEPSEEK_BASE_URL, AiProvider.DEEPSEEK_MODEL),
        )
        // N01：官方 URL + 其他模型仍是自定义配置，不能隐藏真实模型。
        assertEquals(
            AiProvider.Custom,
            AiProvider.fromConfig(AiProvider.DEEPSEEK_BASE_URL, "gpt-4o-mini"),
        )
        assertEquals(
            AiProvider.Custom,
            AiProvider.fromConfig("https://custom.example/v1", AiProvider.DEEPSEEK_MODEL),
        )
    }

    @Test
    fun `legacy preset model is identified for one-time migration`() {
        assertTrue(AiProvider.isLegacyDeepSeekModel("deepseek-v4-flash-vision-exp"))
        assertFalse(AiProvider.isLegacyDeepSeekModel(AiProvider.DEEPSEEK_MODEL))
        assertFalse(AiProvider.isLegacyDeepSeekModel("gpt-4o-mini"))
    }
}
