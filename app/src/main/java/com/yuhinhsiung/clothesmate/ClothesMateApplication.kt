package com.yuhinhsiung.clothesmate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。Hilt 依赖图的根（开发文档 §4.2 / 开发计划 P0-04）。
 */
@HiltAndroidApp
class ClothesMateApplication : Application()
