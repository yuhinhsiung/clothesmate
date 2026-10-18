package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.yuhinhsiung.clothesmate.core.ai.AiRecognition
import com.yuhinhsiung.clothesmate.core.ai.ClothingRecognizer
import com.yuhinhsiung.clothesmate.core.ai.ConnectionTestResult
import com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint
import com.yuhinhsiung.clothesmate.core.datastore.AiConfig
import com.yuhinhsiung.clothesmate.core.datastore.AiConfigField
import com.yuhinhsiung.clothesmate.core.datastore.AiConfigPatch
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    @Test
    fun rapidChangesAcrossFieldsAllPersist() = runBlocking {
        val store = RecordingStore(
            initial = AiConfig("http://old.example/v1", "old-key", "old-model"),
            writeDelayMs = 300,
        )
        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val handle = newHandle(store, appScope)
            withTimeout(2_000) { handle.vm.uiState.first { it.loaded } }

            // 连续改三个字段，写入尚未完成；CONFLATED 队列的消息必须包含全部 dirty 字段
            handle.vm.onModelChange("new-model")
            handle.vm.onBaseUrlChange("http://new.example/v1")
            handle.vm.onApiKeyChange("new-key")
            handle.store.clear()

            val deadline = System.currentTimeMillis() + 3_000
            while ((store.current().model != "new-model" ||
                    store.current().baseUrl != "http://new.example/v1" ||
                    store.current().apiKey != "new-key") &&
                System.currentTimeMillis() < deadline
            ) {
                delay(20)
            }
            assertEquals("new-model", store.current().model)
            assertEquals("http://new.example/v1", store.current().baseUrl)
            assertEquals("new-key", store.current().apiKey)
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun latestEditFromReopenedPageWinsOverOlderBufferedEdit() = runBlocking {
        val store = RecordingStore(
            initial = AiConfig("http://example.invalid/v1", "old-key", "old-model"),
            writeDelayMs = 300,
        )
        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val pageA = newHandle(store, appScope)
            withTimeout(2_000) { pageA.vm.uiState.first { it.loaded } }
            pageA.vm.onModelChange("page1-first")
            pageA.vm.onModelChange("page1-buffered")
            pageA.store.clear()

            // 页面 A 的写入/缓冲尚未结束，页面 B 立即重进并修正同一字段
            val pageB = newHandle(store, appScope)
            withTimeout(2_000) { pageB.vm.uiState.first { it.loaded } }
            pageB.vm.onModelChange("page2-final")
            pageB.store.clear()

            val deadline = System.currentTimeMillis() + 3_000
            while (store.current().model != "page2-final" && System.currentTimeMillis() < deadline) {
                delay(20)
            }
            assertEquals("最后输入必须获胜", "page2-final", store.current().model)
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun reentryWithStaleSnapshotMergesFieldsInsteadOfOverwriting() = runBlocking {
        val store = RecordingStore(
            initial = AiConfig("http://a.example/v1", "sk-test", "old-model"),
            writeDelayMs = 800,
        )
        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val first = newHandle(store, appScope)
            withTimeout(2_000) { first.vm.uiState.first { it.loaded } }
            first.vm.onModelChange("new-model")
            // 页面 1 退出；写入仍在延迟中（模拟 DataStore 提交边界被阻塞）
            first.store.clear()

            // 立即重进：此时 DataStore 仍是旧快照
            val second = newHandle(store, appScope)
            withTimeout(2_000) { second.vm.uiState.first { it.loaded } }
            assertEquals("重进时应先看到旧快照", "old-model", second.vm.uiState.value.model)
            // 只改 Base URL，不能把模型改回 old-model
            second.vm.onBaseUrlChange("http://b.example/v1")
            second.store.clear()

            val deadline = System.currentTimeMillis() + 3_000
            while ((store.current().baseUrl != "http://b.example/v1" ||
                    store.current().model != "new-model") &&
                System.currentTimeMillis() < deadline
            ) {
                delay(20)
            }
            assertEquals("http://b.example/v1", store.current().baseUrl)
            assertEquals("new-model", store.current().model)
        } finally {
            appScope.cancel()
        }
    }

    private class VmHandle(val vm: SettingsViewModel, val store: ViewModelStore)

    private fun newHandle(
        store: UserSettingsStore,
        appScope: CoroutineScope,
    ): VmHandle {
        val vmStore = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(store, NoopRecognizer, appScope) as T
        }
        val vm = ViewModelProvider(vmStore, factory)[SettingsViewModel::class.java]
        return VmHandle(vm, vmStore)
    }

    private class RecordingStore(
        initial: AiConfig = AiConfig(),
        private val writeDelayMs: Long = 50,
    ) : UserSettingsStore {
        private val state = MutableStateFlow(
            UserSettings(aiConfig = initial, aiConsent = AiConsent.Unasked),
        )
        private val mutex = Mutex()
        private val appliedVersions = mutableMapOf<AiConfigField, Long>()
        val lastSaved = AtomicReference<AiConfig?>(null)
        override val settings = state
        fun current(): AiConfig = state.value.aiConfig
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun saveAiConfig(config: AiConfig) {
            updateAiConfig(AiConfigPatch(config.baseUrl, config.apiKey, config.model))
        }

        override suspend fun updateAiConfig(patch: AiConfigPatch) {
            mutex.withLock {
                delay(writeDelayMs)
                val current = state.value.aiConfig
                val accepted = mutableSetOf<AiConfigField>()
                fun accepts(field: AiConfigField): Boolean {
                    val version = patch.versions[field] ?: return true
                    if (version <= 0L) return true
                    return if (version > (appliedVersions[field] ?: 0L)) {
                        accepted += field
                        true
                    } else {
                        false
                    }
                }
                val next = current.copy(
                    baseUrl = if (patch.baseUrl != null && accepts(AiConfigField.BASE_URL)) {
                        patch.baseUrl.trim()
                    } else {
                        current.baseUrl
                    },
                    apiKey = if (patch.apiKey != null && accepts(AiConfigField.API_KEY)) {
                        patch.apiKey.trim()
                    } else {
                        current.apiKey
                    },
                    model = if (patch.model != null && accepts(AiConfigField.MODEL)) {
                        patch.model.trim()
                    } else {
                        current.model
                    },
                )
                accepted.forEach { field ->
                    patch.versions[field]?.let { appliedVersions[field] = it }
                }
                lastSaved.set(next)
                state.value = state.value.copy(aiConfig = next)
            }
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
