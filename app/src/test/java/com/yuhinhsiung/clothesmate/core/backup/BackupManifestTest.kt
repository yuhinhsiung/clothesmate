package com.yuhinhsiung.clothesmate.core.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `manifest roundtrip keeps settings and version`() {
        val manifest = BackupManifest(
            exportedAt = 123L,
            settings = BackupSettings(
                aiBaseUrl = "https://api.deepseek.com",
                aiApiKey = "sk-test",
                aiModel = "deepseek-flash",
                weatherApiHost = "abc.qweatherapi.com",
                weatherDeveloperId = "Q123",
                weatherProjectId = "P1",
                weatherKeyId = "K1",
                weatherPrivateKeyPem = "-----BEGIN PRIVATE KEY-----",
                weatherCity = "杭州",
                stylePreference = "简约",
            ),
        )

        val decoded = json.decodeFromString<BackupManifest>(json.encodeToString(manifest))

        assertEquals(BackupManifest.CURRENT_VERSION, decoded.version)
        assertEquals(123L, decoded.exportedAt)
        assertEquals("sk-test", decoded.settings?.aiApiKey)
        assertEquals("K1", decoded.settings?.weatherKeyId)
        assertEquals("杭州", decoded.settings?.weatherCity)
    }

    @Test
    fun `old manifest without settings still decodes`() {
        val decoded = json.decodeFromString<BackupManifest>("""{"version":1,"exportedAt":9}""")
        assertNull(decoded.settings)
        assertEquals(0, decoded.wardrobes.size)
    }
}
