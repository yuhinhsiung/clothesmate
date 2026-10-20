package com.yuhinhsiung.clothesmate.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

internal val Context.clothesMateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clothesmate_preferences",
)

/**
 * DataStore 注入 —— 开发计划 P0-04。
 *
 * 存放编号计数器（§2.1.3）、AI 配置（§2.2.3）、默认城市（M3）、上次使用的衣柜（M1-08）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.clothesMateDataStore
}

/** 计数器实现绑定：接口便于单测替换为内存版 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindModule {

    @Binds
    @Singleton
    abstract fun bindCodeCounterStore(
        impl: DataStoreCodeCounterStore,
    ): CodeCounterStore
}
