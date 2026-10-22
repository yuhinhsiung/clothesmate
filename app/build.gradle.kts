import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.yuhinhsiung.clothesmate"
    compileSdk = 37

    // M5-10: versionCode = major * 10000 + minor * 100 + patch.
    // Release 1.0.0 => 10000; minor/patch each 0..99, avoiding 1.0.100 == 1.1.0.
    val appVersionMajor = 1
    val appVersionMinor = 0
    val appVersionPatch = 0
    require(appVersionMinor in 0..99 && appVersionPatch in 0..99) {
        "minor/patch must be 0..99 so versionCode stays injective (1.0.100 == 1.1.0 is forbidden)"
    }
    val appVersionCode = appVersionMajor * 10000 + appVersionMinor * 100 + appVersionPatch
    val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

    defaultConfig {
        applicationId = "com.yuhinhsiung.clothesmate"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "com.yuhinhsiung.clothesmate.HiltTestRunner"

        // M5-16：GitHub Releases 更新源（可通过 -Pgithub.owner=... 覆盖，默认公开仓库）。
        val githubOwner = providers.gradleProperty("github.owner").orElse("yuhinhsiung").get()
        val githubRepo = providers.gradleProperty("github.repo").orElse("clothesmate").get()
        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        // 与 docs/update-release-checklist.md §7.1 记录的正式证书 SHA-256 保持一致。
        val releaseCertSha256 = providers.gradleProperty("release.cert.sha256")
            .orElse("35B37099BE2F52760E49B536AFF4A24116E88DD6655C72353F32854C698C9809")
            .get()
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseCertSha256\"")
        // M5-11：仅用于演练正式包堆栈还原；默认关闭，不能出现在正常发布包。
        buildConfigField(
            "boolean",
            "CRASH_INJECTION_ENABLED",
            providers.gradleProperty("crashInjection").orElse("false").get(),
        )

        // 裁剪三方库的多余 locale 资源（Application 自身的 label 仍取默认 values/）。
        // 工程当前只有默认 values/（中文），无 values-en/；将来做英文 UI 时再补那份目录。
        androidResources {
            localeFilters += listOf("zh", "en")
        }
    }

    // M5-10: release keystore is user-held. Properties.load restores Java
    // unicode escapes in storeFile (the path contains Chinese characters).
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.isFile) {
            FileInputStream(keystorePropertiesFile).use { load(it) }
        }
    }
    val releaseStorePath = keystoreProperties.getProperty("storeFile")
    val releaseStoreFile = releaseStorePath?.let { rootProject.file(it) }
    val releaseSigningReady =
        releaseStoreFile?.isFile == true &&
            !keystoreProperties.getProperty("storePassword").isNullOrBlank() &&
            !keystoreProperties.getProperty("keyAlias").isNullOrBlank() &&
            !keystoreProperties.getProperty("keyPassword").isNullOrBlank()

    val requestedReleaseTask = gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true)
    }
    if (requestedReleaseTask && !releaseSigningReady) {
        throw GradleException(
            "Release build requires a local keystore.properties with storeFile/storePassword/" +
                "keyAlias/keyPassword. Refusing to fall back to the debug signing key.",
        )
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // M5-10: never fall back to the debug signing key.
            signingConfig = if (releaseSigningReady) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // Room 导出的 schema 同时作为仪器化测试的资产：MigrationTestHelper 要从
    // assets 里按「数据库全限定名/版本.json」读取期望 schema 来校验迁移结果。
    sourceSets {
        getByName("androidTest").assets.srcDir("${rootDir}/app/schemas")
        // M5-13: ship third-party license notices inside the APK.
        getByName("main").assets.srcDir("${rootDir}/licenses")
    }
}

ksp {
    arg("room.schemaLocation", "${rootDir}/app/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.eddsa)
    implementation(libs.work.runtime)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.tensorflow.lite)
    // GPU delegate 必需（2026-10-14 决定，M2-01 抠图要用）：u2netp 端上推理在低端机
    // CPU 下耗时可达数秒，用 GPU delegate 才能守住开发文档 §10「耗时操作可取消、有超时」的
    // 体感底线。代价是 6 条 lint Aligned16KB 警告（litert-gpu 的 .so 非 16KB 页对齐，
    // 仅影响 Android 15+ 的 16KB 页设备）与 APK 体积，已评估接受——本项目 minSdk 26，
    // 16KB 页设备占比低，且真机主流架构（armeabi-v8a）不受该警告影响。
    implementation(libs.tensorflow.lite.gpu)
    // 注意：不加 tensorflow-lite-support。org.tensorflow:tensorflow-lite-support:0.5.0
    // 已是被标记 relocation 的空壳，指向 com.google.ai.edge.litert:litert-support，
    // 而后者同时发布 litert-support 与 litert-support-api 两个共用
    // org.tensorflow.lite.support 命名空间的 artifact，会在 manifest merger 阶段
    // 报 "Namespace used in multiple modules"。抠图模型通过 core TFLite 的
    // Interpreter + ByteBuffer 直接推理，不需要 support 库的封装。

    implementation(libs.vico.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // 真实 DataStore（JVM 侧），见 libs.versions.toml 该条目的说明
    testImplementation(libs.datastore.preferences.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    // MigrationTestHelper（真迁移测试，见 MigrationTest）
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)
    kspAndroidTest(libs.hilt.compiler)
}
