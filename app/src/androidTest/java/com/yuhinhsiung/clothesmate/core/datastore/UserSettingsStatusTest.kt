package com.yuhinhsiung.clothesmate.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R06：测试连接结果必须与**发起测试时的配置快照**绑定。
 * 用户改了端点/Key/模型后，旧请求的结果不能把新配置标成 Verified/Failed。
 */
@RunWith(AndroidJUnit4::class)
class UserSettingsStatusTest {

    @Test
    fun staleStatusResultIsDiscardedAfterConfigChanges() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "status-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val raw: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        try {
            val store = DataStoreUserSettingsStore(raw, TestSecretCipher())
            val oldConfig = AiConfig("http://10.0.2.2:8080/v1", "sk-old", "model-old")
            val newConfig = AiConfig("http://10.0.2.2:8080/v1", "sk-new", "model-new")
            store.saveAiConfig(oldConfig)
            store.saveAiConfig(newConfig)

            // 旧请求晚到：不得覆盖新配置的状态
            store.setAiStatus(AiStatus.Verified, "old-result", expectedConfig = oldConfig)
            val afterStale = store.settings.first()
            assertNotEquals(AiStatus.Verified, afterStale.aiStatus)
            assertEquals(null, afterStale.aiStatusDetail)

            // 当前配置的结果可以写入
            store.setAiStatus(AiStatus.Verified, "new-result", expectedConfig = newConfig)
            val afterCurrent = store.settings.first()
            assertEquals(AiStatus.Verified, afterCurrent.aiStatus)
            assertEquals("new-result", afterCurrent.aiStatusDetail)
        } finally {
            scope.cancel()
            runCatching { file.delete() }
        }
    }

    private class TestSecretCipher : SecretCipher {
        override fun encrypt(plainText: String): String = PREFIX + plainText.reversed()
        override fun decrypt(cipherText: String): String? =
            cipherText.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.reversed()

        private companion object {
            const val PREFIX = "test-enc:"
        }
    }
}
