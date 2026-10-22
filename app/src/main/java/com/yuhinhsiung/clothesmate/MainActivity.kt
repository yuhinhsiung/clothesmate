package com.yuhinhsiung.clothesmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.core.datastore.ThemeMode
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.datastore.isDark
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.update.UpdateManager
import com.yuhinhsiung.clothesmate.ui.component.LocalImageStorage
import com.yuhinhsiung.clothesmate.ui.component.LocalNavResultBus
import com.yuhinhsiung.clothesmate.ui.navigation.NavResultBus
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
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

    /**
     * 导航结果总线注入 [LocalNavResultBus]（review B-01/B-04：SavedStateHandle
     * 回传在 type-safe 导航下失效，改为显式总线）。
     */
    @Inject
    lateinit var navResultBus: NavResultBus

    /** 深色模式偏好（M2-05）：设置页写 DataStore，根主题这里读，立即生效、重启保持。 */
    @Inject
    lateinit var userSettingsStore: UserSettingsStore

    /** M5-16：启动静默检查一次（24h 缓存由 UpdateCheckStore 负责）。 */
    @Inject
    lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        updateManager.checkSilently()
        setContent {
            // 初始值取「跟随系统」= 与旧行为一致；DataStore 首帧到达后按用户选择切换。
            // map 放在 remember 里：在 composition 中直接建 Flow 每次重组都会换实例
            // （lint FlowOperatorInvokedInComposition），也会让 collect 反复重订阅。
            val themeFlow = remember(userSettingsStore) {
                userSettingsStore.settings.map { it.themeMode }
            }
            val themeMode by themeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            ClothesMateTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalImageStorage provides imageStorage,
                    LocalNavResultBus provides navResultBus,
                ) {
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
