package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.ai.ClothingRecognizer
import com.yuhinhsiung.clothesmate.core.ai.ConnectionTestResult
import com.yuhinhsiung.clothesmate.core.ai.EndpointNormalizer
import com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint
import com.yuhinhsiung.clothesmate.core.datastore.AiConfig
import com.yuhinhsiung.clothesmate.core.datastore.AiConsent
import com.yuhinhsiung.clothesmate.core.datastore.AiStatus
import com.yuhinhsiung.clothesmate.core.datastore.ThemeMode
import com.yuhinhsiung.clothesmate.core.datastore.UserSettings
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel —— M2-05 / UI 文档 §7.6。
 *
 * - 三个 AI 字段即时写入 **应用级作用域**（R05）：页面退出/系统返回不会取消最后一次
 *   持久化，避免「输入后 800ms 内退出丢字」；无「保存」按钮，与 mockup-12 一致；
 * - 「测试连接」用当前输入（即使还没落盘）测一次，并把状态点持久化；
 * - 深色模式写 DataStore 后由 MainActivity 的根主题立即响应，重启保持；
 * - Key 只进 DataStore 与内存，**不写日志**（OkHttp 层也没有日志拦截器）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: UserSettingsStore,
    private val recognizer: ClothingRecognizer,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * R05：配置写入走应用级作用域的一次性队列。CONFLATED 保留最新值；
     * VM 退出时 close() 仍会先消费缓冲值，最后输入不会丢。
     */
    private val configChannel = Channel<AiConfig>(Channel.CONFLATED)

    init {
        appScope.launch {
            for (config in configChannel) {
                runCatching { settingsStore.saveAiConfig(config) }
                    .onFailure { android.util.Log.e(TAG, "AI 配置落盘失败", it) }
            }
        }
        viewModelScope.launch {
            val initial = settingsStore.settings.first()
            _uiState.update { it.copy(loaded = true).from(initial) }
        }
        // 状态点 / 授权可能被别处（首次识别弹窗）改写；主题也可能被系统设置改。
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { state ->
                    // 输入中的配置可能还没落盘；此时不要用旧存储状态覆盖本地
                    // 「未验证」预览，否则用户会看到刚改的字段又变回已验证。
                    val sameConfig = state.baseUrl.trim() == settings.aiConfig.baseUrl.trim() &&
                        state.apiKey.trim() == settings.aiConfig.apiKey.trim() &&
                        state.model.trim() == settings.aiConfig.model.trim()
                    state.copy(
                        themeMode = settings.themeMode,
                        aiStatus = if (sameConfig) settings.effectiveAiStatus else state.aiStatus,
                        aiStatusDetail = if (sameConfig) {
                            settings.aiStatusDetail
                        } else {
                            state.aiStatusDetail
                        },
                        aiConsent = settings.aiConsent,
                    )
                }
            }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        appScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value, aiStatus = previewStatus(value, it.apiKey, it.model)) }
        enqueueConfig()
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, aiStatus = previewStatus(it.baseUrl, value, it.model)) }
        enqueueConfig()
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(model = value, aiStatus = previewStatus(it.baseUrl, it.apiKey, value)) }
        enqueueConfig()
    }

    /** 输入框失焦/IME Done 时可显式再投递一次（当前实现已在每次变更时投递） */
    fun flushConfig() = enqueueConfig()

    private fun enqueueConfig() {
        configChannel.trySend(currentConfig())
    }

    private fun currentConfig(): AiConfig = _uiState.value.let {
        AiConfig(baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model)
    }

    /**
     * 测试连接。
     *
     * 先落盘（保证状态点与配置一致），再用**当前输入**发起一次极小请求；
     * 失败只更新红点与原因，不弹系统错误。
     */
    fun testConnection() {
        val state = _uiState.value
        val config = AiConfig(state.baseUrl, state.apiKey, state.model)
        if (!config.isConfigured) {
            _uiState.update { it.copy(error = SettingsError.Incomplete) }
            return
        }
        if (EndpointNormalizer.chatCompletionsUrl(config.baseUrl) == null) {
            _uiState.update { it.copy(error = SettingsError.InvalidUrl) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true, error = null) }
            settingsStore.saveAiConfig(config)
            val endpoint = RecognitionEndpoint(config.baseUrl, config.apiKey, config.model)
            val result = try {
                recognizer.testConnection(endpoint)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                ConnectionTestResult(false, failure.javaClass.simpleName)
            }
            val status = if (result.success) AiStatus.Verified else AiStatus.Failed
            // R06：状态与发起测试时的配置快照绑定；期间改过配置则存储层丢弃
            settingsStore.setAiStatus(status, result.detail, expectedConfig = config)
            _uiState.update { state ->
                if (currentConfigMatches(state, config)) {
                    state.copy(
                        testing = false,
                        aiStatus = status,
                        aiStatusDetail = result.detail,
                    )
                } else {
                    // 过期结果：新配置仍是「未验证」，不显示旧结果
                    state.copy(testing = false)
                }
            }
        }
    }

    fun onConsentChange(granted: Boolean) {
        val consent = if (granted) AiConsent.Granted else AiConsent.Declined
        _uiState.update { it.copy(aiConsent = consent) }
        appScope.launch { settingsStore.setAiConsent(consent) }
    }

    fun onWeatherCityChange(city: String) {
        _uiState.update { it.copy(weatherCity = city) }
        appScope.launch { settingsStore.setWeatherCity(city) }
    }

    fun onWeatherKeyChange(key: String) {
        _uiState.update { it.copy(weatherApiKey = key) }
        appScope.launch { settingsStore.setWeatherApiKey(key) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun previewStatus(baseUrl: String, apiKey: String, model: String): AiStatus =
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            AiStatus.NotConfigured
        } else {
            AiStatus.Unverified
        }

    private fun currentConfigMatches(state: SettingsUiState, config: AiConfig): Boolean =
        state.baseUrl.trim() == config.baseUrl.trim() &&
            state.apiKey.trim() == config.apiKey.trim() &&
            state.model.trim() == config.model.trim()

    override fun onCleared() {
        // close 不会丢弃已缓冲的最后一个配置；应用级 collector 会把它写完
        configChannel.close()
    }

    private companion object {
        const val TAG = "Settings"
    }
}

/** 设置页 UI 状态 */
data class SettingsUiState(
    val loaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val aiStatus: AiStatus = AiStatus.NotConfigured,
    /** 失败原因（不含 Key） */
    val aiStatusDetail: String? = null,
    val aiConsent: AiConsent = AiConsent.Unasked,
    val testing: Boolean = false,
    /** 非阻塞错误提示：Incomplete / InvalidUrl */
    val error: SettingsError? = null,
    val weatherCity: String = "",
    val weatherApiKey: String = "",
)

enum class SettingsError {
    Incomplete,
    InvalidUrl,
}

private fun SettingsUiState.from(settings: UserSettings): SettingsUiState = copy(
    themeMode = settings.themeMode,
    baseUrl = settings.aiConfig.baseUrl,
    apiKey = settings.aiConfig.apiKey,
    model = settings.aiConfig.model,
    aiStatus = settings.effectiveAiStatus,
    aiStatusDetail = settings.aiStatusDetail,
    aiConsent = settings.aiConsent,
    weatherCity = settings.weatherCity,
    weatherApiKey = settings.weatherApiKey,
)
