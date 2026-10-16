package com.yuhinhsiung.clothesmate.ui.component

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.ui.theme.LocalReducedMotion
import com.yuhinhsiung.clothesmate.ui.theme.muted
import java.io.File

/**
 * 图片加载统一入口（review U-o：Coil 零配置 → muted 占位 + crossfade + 相对路径解析）。
 *
 * ## 三类入参的解析口径
 *
 * DB 只存相对路径（红线 R-1，如 `clothing/original/3.jpg`），Coil 不认识——本组件是
 * **全工程唯一**做「相对路径 → File」转换的地方（经 [LocalImageStorage].toFile，注入面
 * 集中且带 `..` 逃逸校验）。其余两类：
 * - `content://…`（Photo Picker 产出）/ `file://…`：按 Uri 交给 Coil；
 * - 绝对路径（暂存图 `cache/tmp_images/staged_1.jpg`，保存前先展示）：按 File 交给 Coil。
 *
 * ## 展示兜底
 *
 * 占位与加载失败都画 `muted` 底（UI 文档 §11「AsyncImage 占位用骨架色 muted」），
 * 不出现破图图标；透明抠图 PNG（M2 产出）铺 muted 底正合适。
 * crossfade 尊重「减弱动态效果」。
 *
 * `model` 为 null/空（无图）时不渲染图，由调用方的 muted 容器呈现占位图标。
 */
@Composable
fun CmAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (model.isNullOrBlank()) return
    val context = LocalContext.current
    val storage = LocalImageStorage.current
    val reducedMotion = LocalReducedMotion.current
    val mutedColor = MaterialTheme.colorScheme.muted
    val placeholder = remember(mutedColor) { ColorPainter(mutedColor) }

    val request = remember(model, reducedMotion) {
        ImageRequest.Builder(context)
            .data(resolveModel(storage, model))
            .crossfade(enable = !reducedMotion)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        modifier = modifier,
    )
}

/** 三类路径形态 → Coil 可加载对象（见组件 KDoc 的解析口径）。 */
private fun resolveModel(storage: ImageStorage, raw: String): Any = when {
    raw.startsWith("content://") || raw.startsWith("file://") -> Uri.parse(raw)
    raw.startsWith("/") -> File(raw)
    else -> storage.toFile(raw)
        ?: File(raw) // toFile 拒绝时（路径非法）退回 File，让 Coil 报错而不是 NPE
}
