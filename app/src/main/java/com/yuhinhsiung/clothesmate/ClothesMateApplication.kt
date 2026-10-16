package com.yuhinhsiung.clothesmate

import android.app.Application
import com.yuhinhsiung.clothesmate.core.database.TagDictionarySeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。Hilt 依赖图的根（开发文档 §4.2 / 开发计划 P0-04）。
 */
@HiltAndroidApp
class ClothesMateApplication : Application() {

    /**
     * 标签词典预设种子（M1-21）：预设风格/场景标签随启动幂等写入 tag_dictionary，
     * 与自定义标签同权展示。写在应用作用域协程里，不阻塞启动。
     */
    @Inject
    lateinit var tagDictionarySeeder: TagDictionarySeeder

    override fun onCreate() {
        super.onCreate()
        tagDictionarySeeder.seedOnStart()
    }
}
