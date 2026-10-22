package com.yuhinhsiung.clothesmate.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRedactorTest {

    @Test
    fun `redacts sk keys and bearer tokens`() {
        val out = CrashRedactor.redact("key=sk-abcdefgh12345 Authorization: Bearer abc.def.ghi")
        assertFalse(out.contains("abcdefgh12345"))
        assertFalse(out.contains("abc.def.ghi"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `redacts pem private key block`() {
        val pem = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASC
            -----END PRIVATE KEY-----
        """.trimIndent()
        val out = CrashRedactor.redact(pem)
        assertTrue(out.contains("[REDACTED_PRIVATE_KEY]"))
        assertFalse(out.contains("MIIEvQIBADAN"))
    }

    @Test
    fun `redacts url query values but keeps key names`() {
        val out = CrashRedactor.redact("https://api.example.com/v1?api_key=secret-value&city=hangzhou")
        assertFalse(out.contains("secret-value"))
        assertTrue(out.contains("api_key=[REDACTED]"))
        assertTrue(out.contains("city=hangzhou"))
    }

    @Test
    fun `redacts private image paths`() {
        val out = CrashRedactor.redact("failed to read /data/user/0/com.yuhinhsiung.clothesmate/files/clothing/original/3.jpg")
        assertTrue(out.contains("[REDACTED_PATH]"))
        assertFalse(out.contains("/files/clothing/original/3.jpg"))
    }

    @Test
    fun `redacts sql text and response bodies`() {
        val out = CrashRedactor.redact("SQLITE_ERROR: SELECT * FROM clothing_item\nresponseBody={\"secret\":\"x\"}")
        assertTrue(out.contains("[REDACTED_SQL]"))
        assertFalse(out.contains("FROM clothing_item"))
        assertFalse(out.contains("\"secret\""))
    }

    @Test
    fun `keeps ordinary diagnostics readable`() {
        val text = "java.lang.IllegalStateException: empty wardrobe\n\tat com.example.Foo.bar(Foo.kt:12)"
        val out = CrashRedactor.redact(text)
        assertTrue(out.contains("empty wardrobe"))
        assertTrue(out.contains("Foo.kt:12"))
    }
}
