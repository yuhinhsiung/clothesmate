package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * 图片模块注入 —— 开发计划 M1-03 / CLAUDE.md 架构 `core/image/`。
 *
 * 对应开发文档 §3.3 的 `/files/images/`：实现按 `context.filesDir/images/` 解释
 * （等价文件系统路径 `/data/data/<包名>/files/images/`，与 §3.3 前缀一致）。
 * 文档正文只说「图片存应用私有目录」未逐字定义根目录，此为已取口径，登记在开发计划 M1-03 完成记录。
 *
 * `@Provides`（images 根目录）与 `@Binds`（两个实现）分成两个 Module：
 * Kotlin object 无法同时容纳两者——@Binds 必须是 abstract class 的抽象方法。
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageProvidesModule {

    @Provides
    @Singleton
    fun provideImagesRoot(@ApplicationContext context: Context): File =
        File(context.filesDir, IMAGES_DIR)

    private const val IMAGES_DIR = "images"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageBindModule {

    /**
     * 实现的构造依赖（`imagesRoot: File`、`@IoDispatcher`）由 Hilt 自动解析：
     * [provideImagesRoot] 提供前者，[com.yuhinhsiung.clothesmate.core.di.DispatchersModule] 提供后者。
     */
    @Binds
    @Singleton
    abstract fun bindImageStorage(impl: DefaultImageStorage): ImageStorage

    @Binds
    @Singleton
    abstract fun bindImageCompressor(impl: DefaultImageCompressor): ImageCompressor
}
