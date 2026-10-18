package com.yuhinhsiung.clothesmate.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * M2-05 的 Key 加密（用户 2026-10-18 拍板）：
 * - Keystore AES-GCM 能正确往返、密文不含明文、篡改后返回 null；
 * - DataStore 里落盘的是密文，不是明文 Key。
 */
@RunWith(AndroidJUnit4::class)
class SecretCipherTest {

    @Test
    fun keystoreRoundTripAndCiphertextHidesPlaintext() {
        val cipher = AndroidKeystoreSecretCipher()
        val plain = "sk-secret-${UUID.randomUUID()}"
        val encrypted = cipher.encrypt(plain)

        assertEquals(plain, cipher.decrypt(encrypted))
        assertFalse("密文不得包含明文 Key", encrypted.contains(plain))
        // 每次加密随机 IV，同一明文的密文应不同
        assertNotEquals(encrypted, cipher.encrypt(plain))
    }

    @Test
    fun tamperedCiphertextReturnsNull() {
        val cipher = AndroidKeystoreSecretCipher()
        val encrypted = cipher.encrypt("sk-secret")
        val tampered = encrypted.dropLast(2) + "AA"
        assertNull(cipher.decrypt(tampered))
        assertNull(cipher.decrypt("not-base64!!"))
    }

    @Test
    fun dataStoreStoresCiphertextNotPlaintext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val rawStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        try {
            val store = DataStoreUserSettingsStore(rawStore, FakeSecretCipher())
            store.saveAiConfig(AiConfig("http://10.0.2.2:8080/v1", "sk-plain-secret", "mock-vl"))

            // 落盘值：假密文前缀 + 反转，不得包含明文
            val persisted = rawStore.data.first()[stringPreferencesKey("ai_api_key")].orEmpty()
            assertTrue("应写入密文，实际=[$persisted]", persisted.startsWith("fake-enc:"))
            assertFalse("DataStore 不得包含明文 Key", persisted.contains("sk-plain-secret"))

            // 对外读取仍应拿到明文
            val settings = store.settings.first()
            assertEquals("sk-plain-secret", settings.aiConfig.apiKey)
            assertEquals(AiStatus.Unverified, settings.aiStatus)
        } finally {
            scope.cancel()
            runCatching { file.delete() }
        }
    }
}
