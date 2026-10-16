package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 「上次使用的衣柜」DataStore key —— 选择衣柜弹层（M1-08）写入，录入确认页（M1-14）
 * 读取做衣柜预选。集中一处避免两处 VM 各持一份字面量。
 */
const val KEY_LAST_WARDROBE = "last_wardrobe_id"

/** 读上次使用的衣柜 id；无记录（或该衣柜已不存在）返回 null。 */
suspend fun readLastWardrobeId(dataStore: DataStore<Preferences>): Long? =
    dataStore.data.first()[longPreferencesKey(KEY_LAST_WARDROBE)]
