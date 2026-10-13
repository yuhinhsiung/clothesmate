// AGP 9 起 Kotlin 支持内置于 AGP，不再需要 org.jetbrains.kotlin.android 插件
// （该插件会被 AGP 9 直接拒绝）。Kotlin 版本由 libs.versions.toml 的 kotlin 项经
// compose / serialization 编译器插件间接对齐。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
