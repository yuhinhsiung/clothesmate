package com.yuhinhsiung.clothesmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.ui.component.LocalImageStorage
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMateTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 单 Activity 架构，全 App 内容由 Compose + Navigation 承载（开发文档 §4.2）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 图片存储注入 [LocalImageStorage]（U-o：相对路径解析全工程集中在 CmAsyncImage）。
     * MainActivity 是 @AndroidEntryPoint 的注入点，向 Composition 树下发。
     */
    @Inject
    lateinit var imageStorage: ImageStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ClothesMateTheme {
                CompositionLocalProvider(LocalImageStorage provides imageStorage) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ClothesMateNavHost()
                    }
                }
            }
        }
    }
}
