package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class QWeatherJwtProviderTest {

    @Test
    fun `token has official header payload and valid ed25519 signature`() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val auth = auth(pair)
        val provider = QWeatherJwtProvider()

        val token = provider.token(auth, nowEpochSeconds = 1_700_000_000)
        val parts = token.split(".")
        assertEquals(3, parts.size)

        val header = Json.parseToJsonElement(decode(parts[0])).jsonObject
        assertEquals("EdDSA", header["alg"]?.jsonPrimitive?.content)
        assertEquals("KEY123", header["kid"]?.jsonPrimitive?.content)
        assertFalse(header.containsKey("typ"))

        val payload = Json.parseToJsonElement(decode(parts[1])).jsonObject
        assertEquals("Q1234567890", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("ABCDEF123456", payload["sub"]?.jsonPrimitive?.content)
        assertEquals(1_699_999_970L, payload["iat"]?.jsonPrimitive?.long)
        assertEquals(1_699_999_970L + 900, payload["exp"]?.jsonPrimitive?.long)

        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(pair.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
        assertTrue(verifier.verify(decodeBytes(parts[2])))
    }

    @Test
    fun `token is cached until near expiry`() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val provider = QWeatherJwtProvider()
        val first = provider.token(auth(pair), 1_700_000_000)
        val second = provider.token(auth(pair), 1_700_000_100)
        assertEquals(first, second)
        val expired = provider.token(auth(pair), 1_700_000_900)
        assertNotEquals(first, expired)
    }

    @Test
    fun `incomplete auth and bad pem are rejected`() {
        val provider = QWeatherJwtProvider()
        assertTrue(
            runCatching {
                provider.token(WeatherAuthConfig(apiHost = "h.example"), 1_700_000_000)
            }.isFailure,
        )
        assertFalse(provider.validatePrivateKey("not-a-pem"))
    }

    @Test
    fun `api host normalization accepts assigned host`() {
        assertEquals(
            "abc.qweatherapi.com",
            normalizeWeatherApiHost("https://abc.qweatherapi.com/"),
        )
        assertEquals(null, normalizeWeatherApiHost("https://abc.qweatherapi.com/v7"))
    }

    private fun auth(pair: KeyPair): WeatherAuthConfig {
        val pem = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(pair.private.encoded)
        return WeatherAuthConfig(
            apiHost = "abc.qweatherapi.com",
            developerId = "Q1234567890",
            projectId = "ABCDEF123456",
            keyId = "KEY123",
            privateKeyPem = "-----BEGIN PRIVATE KEY-----\n$pem\n-----END PRIVATE KEY-----",
        )
    }

    private fun decode(part: String): String =
        String(decodeBytes(part), Charsets.UTF_8)

    private fun decodeBytes(part: String): ByteArray =
        Base64.getUrlDecoder().decode(part)
}
