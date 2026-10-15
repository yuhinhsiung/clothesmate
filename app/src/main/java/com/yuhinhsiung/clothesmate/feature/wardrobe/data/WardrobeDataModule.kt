package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeConflictChecker
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

    /**
     * 编号冲突判定（review C-05）：`ClothingRepository` 实现了 [CodeConflictChecker]，
     * 但 @Binds **不会**沿继承链传递绑定——[com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator]
     * 要注入它必须显式声明。缺这一行时 CodeAllocator 一旦有注入点就编译失败。
     */
    @Binds
    @Singleton
    abstract fun bindCodeConflictChecker(
        impl: DefaultClothingRepository,
    ): CodeConflictChecker
}
