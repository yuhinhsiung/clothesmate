package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.weather.QWeatherJwtProvider
import com.yuhinhsiung.clothesmate.core.weather.WeatherAuthConfig
import com.yuhinhsiung.clothesmate.core.weather.normalizeWeatherApiHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 和风天气 JWT 配置页状态。私钥只在本页内存中出现，保存后立即交给加密存储。 */
data class WeatherAuthUiState(
    val loaded: Boolean = false,
    val apiHost: String = "",
    val developerId: String = "",
    val projectId: String = "",
    val keyId: String = "",
    val privateKeyPem: String = "",
    val hasStoredKey: Boolean = false,
    val saving: Boolean = false,
    val error: WeatherAuthError? = null,
)

enum class WeatherAuthError {
    Incomplete,
    InvalidHost,
    InvalidPrivateKey,
    SaveFailed,
}

@HiltViewModel
class WeatherAuthViewModel @Inject constructor(
    private val settingsStore: UserSettingsStore,
    private val jwtProvider: QWeatherJwtProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherAuthUiState())
    val uiState: StateFlow<WeatherAuthUiState> = _uiState.asStateFlow()

    /** 已保存的私钥只在内存中用于「留空不修改」，不回显到输入框。 */
    private var storedPrivateKey: String = ""

    init {
        viewModelScope.launch {
            val auth = settingsStore.settings.first().weatherAuth
            storedPrivateKey = auth.privateKeyPem
            _uiState.value = WeatherAuthUiState(
                loaded = true,
                apiHost = auth.apiHost,
                developerId = auth.developerId,
                projectId = auth.projectId,
                keyId = auth.keyId,
                hasStoredKey = auth.privateKeyPem.isNotBlank(),
            )
        }
    }

    fun onApiHostChange(value: String) = _uiState.update { it.copy(apiHost = value, error = null) }
    fun onDeveloperIdChange(value: String) = _uiState.update { it.copy(developerId = value, error = null) }
    fun onProjectIdChange(value: String) = _uiState.update { it.copy(projectId = value, error = null) }
    fun onKeyIdChange(value: String) = _uiState.update { it.copy(keyId = value, error = null) }
    fun onPrivateKeyChange(value: String) = _uiState.update { it.copy(privateKeyPem = value, error = null) }

    /**
     * 保存前校验：API Host 可归一化、四项凭据完整、私钥能完成一次 Ed25519 签名。
     * 校验失败不落盘，避免把「看起来填了」但实际无法签名的配置带到天气页。
     */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.saving) return
        val host = normalizeWeatherApiHost(state.apiHost)
        if (host == null) {
            _uiState.update { it.copy(error = WeatherAuthError.InvalidHost) }
            return
        }
        val effectiveKey = state.privateKeyPem.ifBlank { storedPrivateKey }
        if (state.developerId.isBlank() || state.projectId.isBlank() ||
            state.keyId.isBlank() || effectiveKey.isBlank()
        ) {
            _uiState.update { it.copy(error = WeatherAuthError.Incomplete) }
            return
        }

        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val validKey = withContext(Dispatchers.Default) {
                jwtProvider.validatePrivateKey(effectiveKey)
            }
            if (!validKey) {
                _uiState.update { it.copy(saving = false, error = WeatherAuthError.InvalidPrivateKey) }
                return@launch
            }
            val config = WeatherAuthConfig(
                apiHost = host,
                developerId = state.developerId.trim(),
                projectId = state.projectId.trim(),
                keyId = state.keyId.trim(),
                privateKeyPem = effectiveKey.trim(),
            )
            runCatching { settingsStore.setWeatherAuth(config) }
                .onSuccess {
                    storedPrivateKey = config.privateKeyPem
                    _uiState.update {
                        it.copy(saving = false, privateKeyPem = "", hasStoredKey = true)
                    }
                    onSaved()
                }
                .onFailure {
                    _uiState.update { it.copy(saving = false, error = WeatherAuthError.SaveFailed) }
                }
        }
    }
}
