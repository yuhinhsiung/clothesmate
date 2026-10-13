package com.yuhinhsiung.clothesmate.core.di

import javax.inject.Qualifier

/** 调度器限定符 —— 开发计划 P0-04。 */

/** IO：Room、文件、网络等阻塞操作 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Default：纯计算（抠图后处理、颜色聚类、推荐打分） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Main：UI 线程（默认，一般无需显式注入；保留以便测试替换） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** 应用级协程作用域：任务生命周期长于单个页面（后台抠图 / AI 队列） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
