package com.yuhinhsiung.clothesmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMateTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 架构，全 App 内容由 Compose + Navigation 承载（开发文档 §4.2）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ClothesMateTheme {
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
