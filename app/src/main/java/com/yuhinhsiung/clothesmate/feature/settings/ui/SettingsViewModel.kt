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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * - 三个 AI 字段采用 **800ms 防抖自动保存**（无「保存」按钮，与 mockup-12 一致）；
 * - 「测试连接」用当前输入（即使还没落盘）测一次，并把状态点持久化；
 * - 深色模式写 DataStore 后由 MainActivity 的根主题立即响应，重启保持；
 * - Key 只进 DataStore 与内存，**不写日志**（OkHttp 层也没有日志拦截器）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: UserSettingsStore,
    private val recognizer: ClothingRecognizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val initial = settingsStore.settings.first()
            _uiState.update { it.copy(loaded = true).from(initial) }
        }
        // 状态点 / 授权可能被别处（首次识别弹窗）改写；主题也可能被系统设置改。
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        themeMode = settings.themeMode,
                        aiStatus = settings.effectiveAiStatus,
                        aiStatusDetail = settings.aiStatusDetail,
                        aiConsent = settings.aiConsent,
                    )
                }
            }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value, aiStatus = previewStatus(value, it.apiKey, it.model)) }
        scheduleSave()
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, aiStatus = previewStatus(it.baseUrl, value, it.model)) }
        scheduleSave()
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(model = value, aiStatus = previewStatus(it.baseUrl, it.apiKey, value)) }
        scheduleSave()
    }

    /** 输入框失焦/IME Done 时立即落盘（防抖任务未到点也不丢） */
    fun flushConfig() {
        saveJob?.cancel()
        viewModelScope.launch { persistConfig(_uiState.value) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            persistConfig(_uiState.value)
        }
    }

    private suspend fun persistConfig(state: SettingsUiState) {
        val config = AiConfig(baseUrl = state.baseUrl, apiKey = state.apiKey, model = state.model)
        settingsStore.saveAiConfig(config)
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
        saveJob?.cancel()
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
            settingsStore.setAiStatus(status, result.detail)
            _uiState.update {
                it.copy(
                    testing = false,
                    aiStatus = status,
                    aiStatusDetail = result.detail,
                )
            }
        }
    }

    fun onConsentChange(granted: Boolean) {
        val consent = if (granted) AiConsent.Granted else AiConsent.Declined
        _uiState.update { it.copy(aiConsent = consent) }
        viewModelScope.launch { settingsStore.setAiConsent(consent) }
    }

    fun onWeatherCityChange(city: String) {
        _uiState.update { it.copy(weatherCity = city) }
        viewModelScope.launch { settingsStore.setWeatherCity(city) }
    }

    fun onWeatherKeyChange(key: String) {
        _uiState.update { it.copy(weatherApiKey = key) }
        viewModelScope.launch { settingsStore.setWeatherApiKey(key) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun previewStatus(baseUrl: String, apiKey: String, model: String): AiStatus =
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            AiStatus.NotConfigured
        } else {
            AiStatus.Unverified
        }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 800L
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
