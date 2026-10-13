package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * wardrobe 模块 Repository 绑定（开发文档 §4.2 MVVM + Repository + UseCase）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WardrobeDataModule {

    @Binds
    @Singleton
    abstract fun bindWardrobeRepository(
        impl: DefaultWardrobeRepository,
    ): WardrobeRepository

    @Binds
    @Singleton
    abstract fun bindClothingRepository(
        impl: DefaultClothingRepository,
    ): ClothingRepository
}
