package com.yuhinhsiung.clothesmate.feature.outfit.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * outfit 模块 Repository 绑定（开发文档 §4.2 MVVM + Repository + UseCase）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OutfitDataModule {

    @Binds
    @Singleton
    abstract fun bindOutfitRepository(
        impl: DefaultOutfitRepository,
    ): OutfitRepository
}
