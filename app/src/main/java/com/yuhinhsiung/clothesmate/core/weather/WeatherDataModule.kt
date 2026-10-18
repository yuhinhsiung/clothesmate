package com.yuhinhsiung.clothesmate.core.weather

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 天气层装配：复用 M2 已提供的通用 Retrofit/OkHttp/Json 单例（@Url 绝对地址调用）。
 * 不新建第二套网络栈，避免两套超时/证书配置漂移。
 */
@Module
@InstallIn(SingletonComponent::class)
object WeatherProvidesModule {

    @Provides
    @Singleton
    fun provideQWeatherApi(retrofit: Retrofit): QWeatherApi = retrofit.create(QWeatherApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherBindModule {

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: DefaultWeatherRepository): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindWeatherCacheStore(impl: DataStoreWeatherCacheStore): WeatherCacheStore

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider
}
