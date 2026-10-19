package com.yuhinhsiung.clothesmate.core.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAuthConfigTest {

    @Test
    fun `credential fingerprint changes when private key changes`() {
        val base = WeatherAuthConfig(
            apiHost = "test.example.com",
            developerId = "Q123",
            projectId = "project",
            keyId = "kid",
            privateKeyPem = "key-a",
        )
        val replacement = base.copy(privateKeyPem = "key-b")
        assertNotEquals(base.credentialFingerprint(), replacement.credentialFingerprint())
    }

    @Test
    fun `credential fingerprint is stable and never contains the key`() {
        val auth = WeatherAuthConfig(privateKeyPem = "secret-private-key")
        assertEquals(auth.credentialFingerprint(), auth.copy().credentialFingerprint())
        assertTrue(auth.credentialFingerprint().matches(Regex("[0-9a-f]{16}")))
        assertTrue(!auth.credentialFingerprint().contains("secret"))
    }

    @Test
    fun `empty private key has stable sentinel fingerprint`() {
        assertEquals("no-key", WeatherAuthConfig().credentialFingerprint())
    }
}
