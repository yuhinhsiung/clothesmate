package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import com.yuhinhsiung.clothesmate.core.image.ImageStorage

/**
 * 图片存储的 CompositionLocal —— 供 UI 层把 DB 相对路径解析成可加载的文件。
 *
 * 由 MainActivity 注入并提供（MainActivity @AndroidEntryPoint）。任何直接拿
 * `ImageStorage` 的场合都该走这里，避免 Composable 层各自注入。
 */
val LocalImageStorage = staticCompositionLocalOf<ImageStorage> {
    error("LocalImageStorage not provided")
}
