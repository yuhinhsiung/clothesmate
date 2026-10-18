package com.yuhinhsiung.clothesmate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.net.URI
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 用户设置（M2-05）—— 外观 / AI 配置 / 天气 / 隐私授权。
 *
 * 全部落 DataStore（开发文档 §2.2.3 的「AI 配置」与 M2-05 验收「Key 存 DataStore」）。
 *
 * ## 为什么不加密 API Key
 *
 * 开发文档与 M2-05 只要求「存 DataStore、不明文进日志」，没有要求加密存储；
 * 本工程也不引入文档未列的加密库（CLAUDE.md 技术栈冻结）。API Key 属于用户自己的
 * 第三方凭据，落在 App 私有 DataStore 内（无需存储权限、不上传任何自有服务器）。
 * 该口径已作为 M2 阶段的文档待确认项上报。
 */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

/** AI 隐私授权状态（开发文档 §7、M2-06）。 */
enum class AiConsent {
    /** 还没问过；首次真正要发识别请求前必须弹一次性告知 */
    Unasked,

    /** 用户点了「同意并继续」 */
    Granted,

    /** 用户点了「暂不使用」——本次配置下不再弹，可随时回设置页开启 */
    Declined,
}

/** AI 配置状态点（UI 文档 §7.6：绿=可用 / 灰=未配置 / 红=失败）。 */
enum class AiStatus {
    NotConfigured,
    Unverified,
    Verified,
    Failed,
}

/** AI 端点配置（开发文档 §2.2.3：Base URL / API Key / 模型名）。 */
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    /** 三项齐全才可能发起识别（缺一项即整体降级，§2.2.3） */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 隐私告知弹窗里展示的「当前端点」（只显示主机名，不展示路径与 Key） */
    val host: String?
        get() {
            val raw = baseUrl.trim()
            if (raw.isEmpty()) return null
            val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw
            } else {
                "https://$raw"
            }
            return runCatching { URI(withScheme).host }.getOrNull()?.takeIf { it.isNotBlank() }
        }
}

/** 用户设置快照（UI 一次订阅，Flow 唯一事实来源）。 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val aiConfig: AiConfig = AiConfig(),
    val aiConsent: AiConsent = AiConsent.Unasked,
    val aiStatus: AiStatus = AiStatus.NotConfigured,
    /** 最近一次测试连接的结果说明（失败原因，不含 Key） */
    val aiStatusDetail: String? = null,
    /** 默认城市（M3 天气使用，M2-05 先落配置） */
    val weatherCity: String = "",
    /** 和风天气 Key（M3 天气使用，M2-05 先落配置） */
    val weatherApiKey: String = "",
) {
    /** 状态点口径：未配置优先于历史验证结果 */
    val effectiveAiStatus: AiStatus
        get() = if (aiConfig.isConfigured) aiStatus else AiStatus.NotConfigured
}

/**
 * AI 配置的**字段级**补丁（M2-R05）。
 *
 * 为什么不是整份 [AiConfig]：设置页可能在上一页的写入尚未落盘时重新进入，
 * 读到旧快照；若此时整份提交，会把上一页刚改的字段覆盖回去。补丁只改用户
 * 本次动过的字段，由存储层在同一个 DataStore edit 事务里与**当前磁盘值**合并。
 */
data class AiConfigPatch(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    /**
     * 每个字段的**用户输入版本号**（R05 第三轮）。版本由设置页在每次编辑时从
     * 进程级单调计数器领取；存储层按字段拒绝晚到的旧版本，解决两个页面实例各自
     * 消费队列导致的「旧页面缓冲输入覆盖新页面最终输入」。
     *
     * 空 map = 兼容旧调用（视为最新，不做版本拒绝）。
     */
    val versions: Map<AiConfigField, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = baseUrl == null && apiKey == null && model == null
}

/** AI 配置的三个可编辑字段（与 `AiConfig` 一一对应） */
enum class AiConfigField { BASE_URL, API_KEY, MODEL }

interface UserSettingsStore {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * 保存 AI 配置。
     *
     * **隐私边界**：Base URL 或 API Key 变化 = 照片会被发往另一个端点/另一份凭据，
     * 因此授权回到 [AiConsent.Unasked]（下一次识别前重新弹一次性告知，M2-06）。
     * 只改模型名不影响端点归属，保留授权，但验证状态回到未验证。
     */
    suspend fun saveAiConfig(config: AiConfig)

    /**
     * 只更新 [patch] 里非 null 的字段，与当前存储值原子合并（M2-R05）。
     * 不改变的字段不会被旧页面快照覆盖；端点/Key 变化仍会重置隐私授权。
     */
    suspend fun updateAiConfig(patch: AiConfigPatch)

    suspend fun setAiConsent(consent: AiConsent)

    /**
     * 记录「测试连接」结果。
     *
     * @param expectedConfig 发起测试时的配置快照（R06）。存储层在同一 edit 事务里比较
     *   当前配置；不一致说明用户在测试期间改了端点/Key/模型，**丢弃过期结果**，
     *   绝不能把未测试的新配置标成 Verified/Failed。null 表示不做快照校验（兼容旧调用）。
     */
    suspend fun setAiStatus(
        status: AiStatus,
        detail: String? = null,
        expectedConfig: AiConfig? = null,
    )

    suspend fun setWeatherCity(city: String)

    suspend fun setWeatherApiKey(key: String)
}

@Singleton
class DataStoreUserSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secretCipher: SecretCipher,
) : UserSettingsStore {

    /**
     * 字段版本判定、提交与水位发布的**统一串行化锁**。
     *
     * 水位只能在这个锁内、且 `dataStore.edit` **成功返回后**推进：
     * 失败事务不改变内存水位，同版本重试仍可落盘；同时不会出现「提交已完成但水位
     * 尚未发布」时另一个旧补丁插入的窗口。
     */
    private val versionMutex = Mutex()

    /** 每个字段已成功提交的最大用户输入版本；进程内有效（未落盘队列不会跨进程） */
    private val appliedVersions = ConcurrentHashMap<AiConfigField, Long>()

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val config = AiConfig(
            baseUrl = prefs[KEY_AI_BASE_URL].orEmpty(),
            // 只对外暴露明文；落盘的是 Keystore 加密后的 Base64（见 saveAiConfig）
            apiKey = prefs[KEY_AI_KEY].orEmpty().let { cipherText ->
                if (cipherText.isEmpty()) "" else secretCipher.decrypt(cipherText).orEmpty()
            },
            model = prefs[KEY_AI_MODEL].orEmpty(),
        )
        val storedStatus = prefs[KEY_AI_STATUS]?.let { key ->
            AiStatus.entries.firstOrNull { it.name == key }
        }
        UserSettings(
            themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            aiConfig = config,
            aiConsent = prefs[KEY_AI_CONSENT]?.let { key ->
                AiConsent.entries.firstOrNull { it.name == key }
            } ?: AiConsent.Unasked,
            aiStatus = if (config.isConfigured) storedStatus ?: AiStatus.Unverified else AiStatus.NotConfigured,
            aiStatusDetail = prefs[KEY_AI_STATUS_DETAIL],
            weatherCity = prefs[KEY_WEATHER_CITY].orEmpty(),
            weatherApiKey = prefs[KEY_WEATHER_KEY].orEmpty().let { cipherText ->
                if (cipherText.isEmpty()) "" else secretCipher.decrypt(cipherText).orEmpty()
            },
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.key }
    }

    override suspend fun saveAiConfig(config: AiConfig) {
        // 兼容旧调用：三字段全量补丁，语义与旧 saveAiConfig 一致。
        updateAiConfig(
            AiConfigPatch(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model,
            ),
        )
    }

    override suspend fun updateAiConfig(patch: AiConfigPatch) {
        if (patch.isEmpty) return
        versionMutex.withLock {
            var accepted: Set<AiConfigField> = emptySet()
            dataStore.edit { prefs ->
                val previous = prefs.readAiConfig()
                val acceptedHere = mutableSetOf<AiConfigField>()
                fun accepts(field: AiConfigField): Boolean {
                    val version = patch.versions[field] ?: return true
                    if (version <= 0L) return true
                    val applied = appliedVersions[field] ?: 0L
                    return if (version > applied) {
                        acceptedHere += field
                        true
                    } else {
                        false
                    }
                }

                val applyBase = patch.baseUrl != null && accepts(AiConfigField.BASE_URL)
                val applyKey = patch.apiKey != null && accepts(AiConfigField.API_KEY)
                val applyModel = patch.model != null && accepts(AiConfigField.MODEL)
                accepted = acceptedHere
                val next = previous.copy(
                    baseUrl = if (applyBase) patch.baseUrl?.trim().orEmpty() else previous.baseUrl,
                    apiKey = if (applyKey) patch.apiKey?.trim().orEmpty() else previous.apiKey,
                    model = if (applyModel) patch.model?.trim().orEmpty() else previous.model,
                )
                if (next == previous) return@edit
                prefs[KEY_AI_BASE_URL] = next.baseUrl
                // 绝不回退明文：加密失败就写空，让用户重新填写（并在设置页表现为未配置）。
                // 旧版本遗留的明文 Key 在读取时解密失败 → 视为空，同样需要重填。
                prefs[KEY_AI_KEY] = if (next.apiKey.isEmpty()) {
                    ""
                } else {
                    runCatching { secretCipher.encrypt(next.apiKey) }.getOrElse { failure ->
                        android.util.Log.e("UserSettings", "API Key 加密失败，未落盘明文", failure)
                        ""
                    }
                }
                prefs[KEY_AI_MODEL] = next.model
                // 端点或凭据变化 → 隐私授权重新询问（M2-06 验收：清空 Key 后再次弹窗）
                if (previous.baseUrl != next.baseUrl || previous.apiKey != next.apiKey) {
                    prefs[KEY_AI_CONSENT] = AiConsent.Unasked.name
                }
                prefs[KEY_AI_STATUS] = if (next.isConfigured) {
                    AiStatus.Unverified.name
                } else {
                    AiStatus.NotConfigured.name
                }
                prefs.remove(KEY_AI_STATUS_DETAIL)
            }
            // 只有 DataStore 真正提交成功才会走到这里；失败事务不推进水位。
            accepted.forEach { field ->
                patch.versions[field]?.let { appliedVersions[field] = it }
            }
        }
    }

    override suspend fun setAiConsent(consent: AiConsent) {
        dataStore.edit { it[KEY_AI_CONSENT] = consent.name }
    }

    override suspend fun setAiStatus(
        status: AiStatus,
        detail: String?,
        expectedConfig: AiConfig?,
    ) {
        dataStore.edit { prefs ->
            val current = prefs.readAiConfig()
            // R06：结果必须属于当前配置；测试期间改过任一字段就丢弃
            if (expectedConfig != null && current != expectedConfig.normalized()) return@edit
            if (!current.isConfigured) {
                prefs[KEY_AI_STATUS] = AiStatus.NotConfigured.name
                prefs.remove(KEY_AI_STATUS_DETAIL)
                return@edit
            }
            prefs[KEY_AI_STATUS] = status.name
            if (detail.isNullOrBlank()) {
                prefs.remove(KEY_AI_STATUS_DETAIL)
            } else {
                prefs[KEY_AI_STATUS_DETAIL] = detail
            }
        }
    }

    override suspend fun setWeatherCity(city: String) {
        dataStore.edit { it[KEY_WEATHER_CITY] = city.trim() }
    }

    override suspend fun setWeatherApiKey(key: String) {
        val trimmed = key.trim()
        dataStore.edit { prefs ->
            prefs[KEY_WEATHER_KEY] = if (trimmed.isEmpty()) {
                ""
            } else {
                runCatching { secretCipher.encrypt(trimmed) }.getOrElse { failure ->
                    android.util.Log.e("UserSettings", "天气 Key 加密失败，未落盘明文", failure)
                    ""
                }
            }
        }
    }

    /** 从 Preferences 还原完整 AI 配置（含解密）；读写两侧共用，避免口径漂移 */
    private fun Preferences.readAiConfig(): AiConfig = AiConfig(
        baseUrl = this[KEY_AI_BASE_URL].orEmpty(),
        apiKey = this[KEY_AI_KEY].orEmpty().let { cipherText ->
            if (cipherText.isEmpty()) "" else secretCipher.decrypt(cipherText).orEmpty()
        },
        model = this[KEY_AI_MODEL].orEmpty(),
    )

    private fun AiConfig.normalized(): AiConfig = copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
    )

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_KEY = stringPreferencesKey("ai_api_key")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_AI_CONSENT = stringPreferencesKey("ai_consent")
        val KEY_AI_STATUS = stringPreferencesKey("ai_status")
        val KEY_AI_STATUS_DETAIL = stringPreferencesKey("ai_status_detail")
        val KEY_WEATHER_CITY = stringPreferencesKey("weather_city")
        val KEY_WEATHER_KEY = stringPreferencesKey("weather_api_key")

    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserSettingsModule {
    @Binds
    @Singleton
    abstract fun bindUserSettingsStore(impl: DataStoreUserSettingsStore): UserSettingsStore

    /** API Key 静态加密（Android Keystore AES-GCM；不引入第三方库） */
    @Binds
    @Singleton
    abstract fun bindSecretCipher(impl: AndroidKeystoreSecretCipher): SecretCipher
}

/**
 * 主题模式 → 是否深色。
 *
 * 单独抽成纯函数：设置页三段切换（M2-05）与 MainActivity 的根主题读同一个口径，
 * 避免「设置页显示深色、实际还是跟随系统」这类分叉。
 */
fun ThemeMode.isDark(systemInDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemInDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** 给测试/预览用的空实现入口（不参与 Hilt 绑定）。 */
@Suppress("unused")
class InMemoryUserSettingsStore(initial: UserSettings = UserSettings()) : UserSettingsStore {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun saveAiConfig(config: AiConfig) {
        state.value = state.value.copy(aiConfig = config)
    }

    override suspend fun updateAiConfig(patch: AiConfigPatch) {
        val current = state.value.aiConfig
        state.value = state.value.copy(
            aiConfig = current.copy(
                baseUrl = patch.baseUrl?.trim() ?: current.baseUrl,
                apiKey = patch.apiKey?.trim() ?: current.apiKey,
                model = patch.model?.trim() ?: current.model,
            ),
        )
    }

    override suspend fun setAiConsent(consent: AiConsent) {
        state.value = state.value.copy(aiConsent = consent)
    }

    override suspend fun setAiStatus(
        status: AiStatus,
        detail: String?,
        expectedConfig: AiConfig?,
    ) {
        if (expectedConfig != null && state.value.aiConfig != expectedConfig) return
        state.value = state.value.copy(aiStatus = status, aiStatusDetail = detail)
    }

    override suspend fun setWeatherCity(city: String) {
        state.value = state.value.copy(weatherCity = city)
    }

    override suspend fun setWeatherApiKey(key: String) {
        state.value = state.value.copy(weatherApiKey = key)
    }
}
