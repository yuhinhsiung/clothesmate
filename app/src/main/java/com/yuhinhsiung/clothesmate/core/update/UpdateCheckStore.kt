package com.yuhinhsiung.clothesmate.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 更新检查的 24h 节流与结果时间缓存。只保存时间/版本，不保存下载包或安装状态。
 *
 * 与主偏好共用同一 DataStore 文件（避免第二份 DataStore 实例），键前缀隔离。
 */
@Singleton
class UpdateCheckStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun lastAttemptAt(): Long = dataStore.data.first()[LAST_ATTEMPT] ?: 0L

    suspend fun lastSuccessAt(): Long = dataStore.data.first()[LAST_SUCCESS] ?: 0L

    suspend fun lastCheckedVersion(): String? = dataStore.data.first()[LAST_VERSION]

    suspend fun shouldCheckSilently(now: Long, currentVersion: String): Boolean {
        val lastAttempt = lastAttemptAt()
        if (lastAttempt <= 0L) return true
        // R08：未来时间戳视为异常（用户手动改过系统时间/时区回拨），
        // 不能让它把静默检查压到“未来时间 + 24h”。让它立即失效并重新记录。
        if (lastAttempt > now) return true
        // 版本变化（覆盖安装）立即检查一次；同一版本 24h 内不重复自动请求。
        if (lastCheckedVersion() != currentVersion) return true
        return now - lastAttempt >= TWENTY_FOUR_HOURS_MS
    }

    suspend fun recordAttempt(now: Long, version: String, successful: Boolean) {
        dataStore.edit { prefs ->
            prefs[LAST_ATTEMPT] = now
            prefs[LAST_VERSION] = version
            if (successful) prefs[LAST_SUCCESS] = now
        }
    }

    private companion object {
        const val TWENTY_FOUR_HOURS_MS = 24L * 60 * 60 * 1000
        val LAST_ATTEMPT = longPreferencesKey("update_last_attempt_at")
        val LAST_SUCCESS = longPreferencesKey("update_last_success_at")
        val LAST_VERSION = stringPreferencesKey("update_last_checked_version")
    }
}
