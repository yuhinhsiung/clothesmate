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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * R05 第四轮根因回归：**失败事务不能推进字段版本水位**。
 *
 * 包装的 DataStore 在 transform 已执行、真实提交前抛一次 IOException，模拟
 * Preferences 提交失败；随后用户不修改输入，仅重试同一版本（测试连接路径），
 * 必须能真正落盘。
 */
@RunWith(AndroidJUnit4::class)
class UserSettingsVersionRetryTest {

    @Test
    fun failedCommitDoesNotAdvanceWatermarkAndSameVersionRetryPersists() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "retry-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val raw: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val failing = FailAfterTransformDataStore(raw)
        try {
            val store = DataStoreUserSettingsStore(failing, TestSecretCipher())
            store.saveAiConfig(AiConfig("http://old.example/v1", "key", "old-model"))

            val patch = AiConfigPatch(
                model = "retry-model",
                versions = mapOf(AiConfigField.MODEL to 1L),
            )
            failing.armFailure()
            val first = runCatching { store.updateAiConfig(patch) }
            assertTrue("首次提交应失败", first.isFailure)
            assertEquals("失败事务不得改变存储", "old-model", store.settings.first().aiConfig.model)

            // 同一版本重试（用户只点测试连接，不再改字）
            store.updateAiConfig(patch)
            assertEquals(
                "失败事务不得推进水位，同版本重试必须落盘",
                "retry-model",
                store.settings.first().aiConfig.model,
            )
        } finally {
            scope.cancel()
            runCatching { file.delete() }
        }
    }

    private class FailAfterTransformDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> {
        override val data = delegate.data
        private var failNext = false

        fun armFailure() {
            failNext = true
        }

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            // 先执行 transform（生产 store 会在这里完成版本判定并记录 accepted），
            // 再在真正提交前失败：这正是旧实现污染内存水位的边界。
            val transformed = transform(delegate.data.first())
            if (failNext) {
                failNext = false
                throw IOException("simulated preferences commit failure")
            }
            return delegate.updateData { transformed }
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
