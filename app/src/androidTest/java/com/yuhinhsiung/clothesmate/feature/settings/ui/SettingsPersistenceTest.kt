package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.yuhinhsiung.clothesmate.core.ai.AiRecognition
import com.yuhinhsiung.clothesmate.core.ai.ClothingRecognizer
import com.yuhinhsiung.clothesmate.core.ai.ConnectionTestResult
import com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint
import com.yuhinhsiung.clothesmate.core.datastore.AiConfig
import com.yuhinhsiung.clothesmate.core.datastore.AiConsent
import com.yuhinhsiung.clothesmate.core.datastore.AiStatus
import com.yuhinhsiung.clothesmate.core.datastore.ThemeMode
import com.yuhinhsiung.clothesmate.core.datastore.UserSettings
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * R05：输入后立刻退出设置页，最后一次配置仍必须落盘。
 *
 * 用真实的 [SettingsViewModel] + [ViewModelStore.clear]（触发 onCleared）
 * 和可控的应用级作用域，验证配置写入不依赖 viewModelScope。
 */
class SettingsPersistenceTest {

    @Test
    fun lastInputSurvivesImmediateViewModelClear() = runBlocking {
        val store = RecordingStore()
        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val viewModelStore = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(store, NoopRecognizer, appScope) as T
        }
        val provider = ViewModelProvider(viewModelStore, factory)
        val viewModel = provider[SettingsViewModel::class.java]

        viewModel.onModelChange("gpt-4o-mini")
        // 立即退出（模拟系统返回/导航），不等防抖
        viewModelStore.clear()

        // 应用级 collector 应把缓冲的最后一个配置写完
        val deadline = System.currentTimeMillis() + 2_000
        while (store.lastSaved.get()?.model != "gpt-4o-mini" && System.currentTimeMillis() < deadline) {
            delay(20)
        }
        assertEquals("gpt-4o-mini", store.lastSaved.get()?.model)
        appScope.cancel()
    }

    private class RecordingStore : UserSettingsStore {
        private val state = MutableStateFlow(UserSettings(aiConsent = AiConsent.Unasked))
        val lastSaved = AtomicReference<AiConfig?>(null)
        override val settings = state
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun saveAiConfig(config: AiConfig) {
            delay(50)
            lastSaved.set(config)
            state.value = state.value.copy(aiConfig = config)
        }

        override suspend fun setAiConsent(consent: AiConsent) = Unit
        override suspend fun setAiStatus(
            status: AiStatus,
            detail: String?,
            expectedConfig: AiConfig?,
        ) = Unit

        override suspend fun setWeatherCity(city: String) = Unit
        override suspend fun setWeatherApiKey(key: String) = Unit
    }

    private object NoopRecognizer : ClothingRecognizer {
        override suspend fun recognize(endpoint: RecognitionEndpoint, imageJpeg: ByteArray): AiRecognition =
            AiRecognition()

        override suspend fun testConnection(endpoint: RecognitionEndpoint): ConnectionTestResult =
            ConnectionTestResult(false, "not used")
    }
}
