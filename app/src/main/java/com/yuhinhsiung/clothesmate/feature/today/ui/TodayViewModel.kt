package com.yuhinhsiung.clothesmate.feature.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.WearException
import com.yuhinhsiung.clothesmate.core.model.WearFailure
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.core.recommendation.FallbackCombination
import com.yuhinhsiung.clothesmate.core.recommendation.RecommendationEngine
import com.yuhinhsiung.clothesmate.core.recommendation.RecommendationInput
import com.yuhinhsiung.clothesmate.core.recommendation.RecommendedOutfit
import com.yuhinhsiung.clothesmate.core.recommendation.WeatherFacts
import com.yuhinhsiung.clothesmate.core.recommendation.buildFallbackCombination
import com.yuhinhsiung.clothesmate.core.weather.LocationProvider
import com.yuhinhsiung.clothesmate.core.weather.WeatherCacheStore
import com.yuhinhsiung.clothesmate.core.weather.WeatherException
import com.yuhinhsiung.clothesmate.core.weather.WeatherFailure
import com.yuhinhsiung.clothesmate.core.weather.WeatherRepository
import com.yuhinhsiung.clothesmate.core.weather.WeatherAuthConfig
import com.yuhinhsiung.clothesmate.core.weather.WeatherSnapshot
import com.yuhinhsiung.clothesmate.feature.outfit.data.OutfitRepository
import com.yuhinhsiung.clothesmate.feature.today.data.ScheduleSelectionStore
import com.yuhinhsiung.clothesmate.feature.today.data.ScheduleTagRepository
import com.yuhinhsiung.clothesmate.feature.today.data.WearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/** 天气卡在今日页的展示状态（M3-01 三态 + 缓存/刷新）。 */
sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data object NoKey : WeatherUiState
    data class NeedCity(val locationDenied: Boolean) : WeatherUiState
    data class Ready(
        val snapshot: WeatherSnapshot,
        val refreshing: Boolean = false,
    ) : WeatherUiState

    data class Failed(
        val cached: WeatherSnapshot?,
        val failure: WeatherFailure,
    ) : WeatherUiState
}

/**
 * 今日页状态（M3-05）。
 *
 * 天气是网络资源，行程/推荐/穿着记录都来自本地；UI 只消费不可变快照，
 * 失败与权限状态显式建模，避免“空数据”把用户困在空白页里。
 */
data class TodayUiState(
    val today: LocalDate = LocalDate.now(),
    val weather: WeatherUiState = WeatherUiState.Loading,
    val weatherCity: String = "",
    val hasWeatherAuth: Boolean = false,
    val stylePreference: String = "",
    val scheduleOptions: List<String> = emptyList(),
    val selectedSchedule: Set<String> = emptySet(),
    val candidates: List<RecommendedOutfit> = emptyList(),
    val visibleRecommendations: List<RecommendedOutfit> = emptyList(),
    val pageIndex: Int = 0,
    val fallback: FallbackCombination? = null,
    val records: List<WearRecord> = emptyList(),
    val confirmingOutfitId: Long? = null,
    /** 非空 = 二次确认弹窗正在展示这套推荐 */
    val pendingWear: RecommendedOutfit? = null,
) {
    val pageCount: Int
        get() = if (candidates.isEmpty()) 0 else (candidates.size + PAGE_SIZE - 1) / PAGE_SIZE

    /** 城市优先显示用户配置值；定位模式下回落到快照里的城市名。 */
    val cityLabel: String
        get() = weatherCity.ifBlank {
            when (val current = weather) {
                is WeatherUiState.Ready -> current.snapshot.city
                is WeatherUiState.Failed -> current.cached?.city.orEmpty()
                else -> ""
            }
        }

    val weatherSnapshotOrNull: WeatherSnapshot?
        get() = when (val current = weather) {
            is WeatherUiState.Ready -> current.snapshot
            is WeatherUiState.Failed -> current.cached
            else -> null
        }

    companion object {
        const val PAGE_SIZE = 3
    }
}

/** 一次性 UI 事件：穿好后的庆祝、换批提示、失败原因。 */
sealed interface TodayEvent {
    data class WearConfirmed(val repeated: Boolean) : TodayEvent
    data object AllRecommendationsSeen : TodayEvent
    data class WearFailed(val failure: WearFailure?) : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val clothingItemDao: ClothingItemDao,
    private val wearRepository: WearRepository,
    private val settingsStore: UserSettingsStore,
    private val weatherRepository: WeatherRepository,
    private val weatherCacheStore: WeatherCacheStore,
    private val locationProvider: LocationProvider,
    private val scheduleSelectionStore: ScheduleSelectionStore,
    private val scheduleTagRepository: ScheduleTagRepository,
    private val json: Json,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(TodayUiState(today = today))
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TodayEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TodayEvent> = _events.asSharedFlow()

    private val selectedSchedule = MutableStateFlow<Set<String>>(emptySet())
    private val fallbackSeed = MutableStateFlow(System.nanoTime())

    /** 设置里的和风天气 JWT 凭据；不进入 UI 状态，避免误显示/泄露。 */
    private var weatherAuth: WeatherAuthConfig = WeatherAuthConfig()
    private val selectedStyle = MutableStateFlow<String?>(null)
    private var weatherJob: Job? = null
    private var weatherLocationConfig: Pair<String, WeatherAuthConfig>? = null
    private var lastWeatherAttemptAt: Long = 0L
    private var lastWeatherFailure: WeatherFailure? = null

    init {
        observeSettings()
        observeSchedule()
        observeRecords()
        observeRecommendations()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings
                .map {
                    UserWeatherSettings(
                        city = it.weatherCity.trim(),
                        auth = it.weatherAuth,
                        style = it.stylePreference.trim(),
                    )
                }
                .distinctUntilChanged()
                .collect { settings ->
                    val configChanged = weatherLocationConfig != (settings.city to settings.auth)
                    weatherLocationConfig = settings.city to settings.auth
                    weatherAuth = settings.auth
                    selectedStyle.value = settings.style.ifBlank { null }
                    _uiState.update {
                        it.copy(
                            weatherCity = settings.city,
                            hasWeatherAuth = settings.auth.isConfigured,
                            stylePreference = settings.style,
                        )
                    }
                    if (configChanged) reloadWeather()
                }
        }
    }

    private fun observeSchedule() {
        viewModelScope.launch {
            scheduleSelectionStore.observe().collect { selection ->
                val tags = if (selection.date == today) selection.tags else emptySet()
                selectedSchedule.value = tags
                _uiState.update { it.copy(selectedSchedule = tags) }
            }
        }
        viewModelScope.launch {
            scheduleTagRepository.observeTags().collect { options ->
                _uiState.update { it.copy(scheduleOptions = options) }
            }
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            wearRepository.observeRecords(today).collect { records ->
                _uiState.update { it.copy(records = records) }
            }
        }
    }

    private fun observeRecommendations() {
        viewModelScope.launch {
            combine(
                outfitRepository.observeAllWithItems(),
                clothingItemDao.observeAll(),
                selectedSchedule,
                _uiState.map { it.weather }.distinctUntilChanged(),
                combine(selectedStyle, fallbackSeed) { style, seed -> style to seed },
            ) { outfits, clothing, schedule, weather, tuning ->
                val (style, seed) = tuning
                val input = RecommendationInput(
                    outfits = outfits,
                    weather = weather.recommendationWeather(),
                    scheduleTags = schedule,
                    today = today,
                    stylePreference = style,
                    allClothing = clothing.map { it.toDomain() }
                        .filter { it.status == ClothingStatus.NORMAL },
                )
                val candidates = RecommendationEngine.recommend(input)
                val fallback = if (candidates.isEmpty()) {
                    buildFallbackCombination(input, seed)
                } else {
                    null
                }
                RecommendationUiSnapshot(
                    candidates = candidates,
                    visible = candidates.take(TodayUiState.PAGE_SIZE),
                    fallback = fallback,
                )
            }.collect { recommendations ->
                _uiState.update {
                    it.copy(
                        candidates = recommendations.candidates,
                        visibleRecommendations = recommendations.visible,
                        pageIndex = 0,
                        fallback = recommendations.fallback,
                    )
                }
            }
        }
    }

    /**
     * 首次进入、配置变更或用户点重试时调用。
     *
     * 成本控制：30 分钟内的缓存直接复用，不发起任何请求；同一地点 60 秒内失败过，
     * 自动重试（页面回到前台/配置流重放）不会再次打接口。用户主动点「重试」传
     * `force = true`，绕过自动冷却。
     */
    fun reloadWeather(force: Boolean = false) {
        weatherJob?.cancel()
        val city = _uiState.value.weatherCity
        val auth = weatherAuth
        if (!auth.isConfigured) {
            updateWeather(WeatherUiState.NoKey)
            return
        }
        weatherJob = viewModelScope.launch {
            if (city.isBlank()) {
                val cached = weatherCacheStore.snapshot.firstOrNull()
                if (!force && cached?.isFresh() == true) {
                    updateWeather(WeatherUiState.Ready(cached))
                    return@launch
                }
                if (!locationProvider.hasCoarsePermission()) {
                    updateWeather(WeatherUiState.NeedCity(locationDenied = true))
                    return@launch
                }
                updateWeather(WeatherUiState.Loading)
                val coordinates = runCatching { locationProvider.getCoarseCoordinates() }.getOrNull()
                if (coordinates == null) {
                    updateWeather(WeatherUiState.NeedCity(locationDenied = true))
                    return@launch
                }
                fetchWeather(coordinates.toQueryValue(), auth, cached, force)
            } else {
                val cached = weatherCacheStore.snapshot.firstOrNull()
                    ?.takeIf { it.matchesCity(city) }
                if (!force && cached?.isFresh() == true) {
                    updateWeather(WeatherUiState.Ready(cached))
                    return@launch
                }
                updateWeather(
                    if (cached != null) {
                        WeatherUiState.Ready(cached, refreshing = true)
                    } else {
                        WeatherUiState.Loading
                    },
                )
                fetchWeather(city, auth, cached, force)
            }
        }
    }

    /** 用户主动重试：允许立即请求，不受自动冷却限制。 */
    fun retryWeather() = reloadWeather(force = true)

    private suspend fun fetchWeather(
        query: String,
        auth: WeatherAuthConfig,
        cached: WeatherSnapshot?,
        force: Boolean,
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWeatherAttemptAt < MIN_AUTO_RETRY_MS) {
            // 冷却期内不重复请求；没有缓存时恢复上一次失败态，避免页面卡在 Loading。
            val failure = lastWeatherFailure
            if (cached != null) {
                updateWeather(WeatherUiState.Ready(cached))
            } else if (failure != null) {
                updateWeather(WeatherUiState.Failed(cached = null, failure = failure))
            }
            return
        }
        lastWeatherAttemptAt = now
        try {
            val snapshot = weatherRepository.fetch(query, auth)
            lastWeatherFailure = null
            updateWeather(WeatherUiState.Ready(snapshot))
        } catch (error: WeatherException) {
            if (error.failure == WeatherFailure.NO_LOCATION) {
                updateWeather(WeatherUiState.NeedCity(locationDenied = true))
            } else {
                lastWeatherFailure = error.failure
                updateWeather(WeatherUiState.Failed(cached = cached, failure = error.failure))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            lastWeatherFailure = WeatherFailure.UNKNOWN
            updateWeather(
                WeatherUiState.Failed(cached = cached, failure = WeatherFailure.UNKNOWN),
            )
        }
    }

    /** 系统定位授权回来：有 Key 才重新取位；没有 Key 仍停在配置引导。 */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            updateWeather(WeatherUiState.NeedCity(locationDenied = true))
            return
        }
        if (_uiState.value.hasWeatherAuth) reloadWeather(force = true)
    }

    fun selectCity(city: String) {
        val cleaned = city.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            settingsStore.setWeatherCity(cleaned)
            // 同一城市重复选择不会触发 settings 的 distinct 流，这里主动刷新一次。
            reloadWeather(force = true)
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            settingsStore.setWeatherCity("")
            reloadWeather(force = true)
        }
    }

    fun toggleSchedule(tag: String) {
        val next = _uiState.value.selectedSchedule.toMutableSet().apply {
            if (!add(tag)) remove(tag)
        }
        applySchedule(next)
    }

    fun addCustomSchedule(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val value = scheduleTagRepository.addCustom(cleaned) ?: return@launch
            applySchedule(_uiState.value.selectedSchedule + value)
        }
    }

    private fun applySchedule(tags: Set<String>) {
        selectedSchedule.value = tags
        _uiState.update { it.copy(selectedSchedule = tags) }
        viewModelScope.launch { scheduleSelectionStore.save(today, tags) }
    }

    fun shuffleRecommendations() {
        val state = _uiState.value
        if (state.candidates.isEmpty()) {
            fallbackSeed.value = System.nanoTime()
            return
        }
        val pages = state.candidates.chunked(TodayUiState.PAGE_SIZE)
        if (pages.size <= 1) {
            _events.tryEmit(TodayEvent.AllRecommendationsSeen)
            return
        }
        val next = (state.pageIndex + 1) % pages.size
        _uiState.update {
            it.copy(pageIndex = next, visibleRecommendations = pages[next])
        }
        if (next == 0) _events.tryEmit(TodayEvent.AllRecommendationsSeen)
    }

    fun shuffleFallback() {
        fallbackSeed.value = System.nanoTime()
    }

    /** 推荐卡/详情页的「今天穿这套」先打开二次确认，不立刻写库。 */
    fun requestWear(outfit: RecommendedOutfit) {
        if (_uiState.value.confirmingOutfitId != null) return
        _uiState.update { it.copy(pendingWear = outfit) }
    }

    fun dismissWearConfirm() {
        _uiState.update { it.copy(pendingWear = null) }
    }

    /** 用户在确认弹窗里点了「确认穿着」；备注只随本次记录保存。 */
    fun confirmPendingWear(note: String) {
        val pending = _uiState.value.pendingWear ?: return
        if (_uiState.value.confirmingOutfitId != null) return
        _uiState.update { it.copy(confirmingOutfitId = pending.outfit.outfit.id) }
        viewModelScope.launch {
            val snapshot = (_uiState.value.weather as? WeatherUiState.Ready)?.snapshot
            val weatherJson = snapshot?.let {
                json.encodeToString(WeatherSnapshot.serializer(), it)
            }
            try {
                val repeated = _uiState.value.records.isNotEmpty()
                wearRepository.confirmWear(
                    outfitId = pending.outfit.outfit.id,
                    date = today,
                    scheduleTags = _uiState.value.selectedSchedule.sorted(),
                    weatherJson = weatherJson,
                    note = note.trim().take(500).ifBlank { null },
                )
                _uiState.update { it.copy(pendingWear = null) }
                _events.emit(TodayEvent.WearConfirmed(repeated = repeated))
            } catch (error: WearException) {
                _events.emit(TodayEvent.WearFailed(error.failure))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.emit(TodayEvent.WearFailed(null))
            } finally {
                _uiState.update { it.copy(confirmingOutfitId = null) }
            }
        }
    }

    fun updateRecordNote(recordId: Long, note: String) {
        viewModelScope.launch {
            wearRepository.updateNote(recordId, note)
        }
    }

    /** 回到今日页时若天气已超过 30 分钟，静默刷新；不打断已有卡片。 */
    fun refreshWeatherIfStale() {
        val snapshot = _uiState.value.weatherSnapshotOrNull ?: return
        val age = System.currentTimeMillis() - snapshot.fetchedAt
        if (age > WEATHER_STALE_MS) reloadWeather()
    }

    private fun updateWeather(weather: WeatherUiState) {
        _uiState.update { it.copy(weather = weather) }
    }

    private data class RecommendationUiSnapshot(
        val candidates: List<RecommendedOutfit>,
        val visible: List<RecommendedOutfit>,
        val fallback: FallbackCombination?,
    )

    private data class UserWeatherSettings(
        val city: String,
        val auth: WeatherAuthConfig,
        val style: String,
    )

    private companion object {
        const val WEATHER_STALE_MS = 30 * 60 * 1000L
        const val MIN_AUTO_RETRY_MS = 60_000L
    }
}

private fun WeatherUiState.recommendationWeather(): WeatherFacts? {
    val snapshot = when (this) {
        is WeatherUiState.Ready -> snapshot
        is WeatherUiState.Failed -> cached
        else -> null
    } ?: return null
    return WeatherFacts(
        minTemp = snapshot.minTemp,
        maxTemp = snapshot.maxTemp,
        phenomenon = snapshot.text,
    )
}

private fun WeatherSnapshot.matchesCity(city: String): Boolean {
    val target = city.trim()
    return target.isNotEmpty() &&
        (this.city == target || this.city.contains(target) || target.contains(this.city))
}
