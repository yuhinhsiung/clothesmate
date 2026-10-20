package com.yuhinhsiung.clothesmate.core.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `manifest roundtrip keeps encrypted settings and version`() {
        val encrypted = BackupCredentialCipher.encrypt("""{"aiApiKey":"sk-test"}""", "secret123")
        val manifest = BackupManifest(
            exportedAt = 123L,
            settings = BackupSettings(
                weatherCity = "杭州",
                stylePreference = "简约",
                encryptedCredentials = encrypted,
            ),
        )

        val decoded = json.decodeFromString<BackupManifest>(json.encodeToString(manifest))

        assertEquals(BackupManifest.CURRENT_VERSION, decoded.version)
        assertEquals(2, decoded.version)
        assertEquals(123L, decoded.exportedAt)
        assertEquals("杭州", decoded.settings?.weatherCity)
        assertEquals("简约", decoded.settings?.stylePreference)
        assertEquals(encrypted, decoded.settings?.encryptedCredentials)
        assertFalse(decoded.toString().contains("sk-test"))
    }

    @Test
    fun `old manifest without settings still decodes`() {
        val decoded = json.decodeFromString<BackupManifest>("""{"version":1,"exportedAt":9}""")
        assertNull(decoded.settings)
        assertEquals(0, decoded.wardrobes.size)
    }
}
