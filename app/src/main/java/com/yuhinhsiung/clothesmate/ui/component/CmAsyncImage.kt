package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.LocalAtelierColors
import com.yuhinhsiung.clothesmate.ui.theme.LocalReducedMotion
import java.io.File

/**
 * Photo Surface 统一承载层（开发文档 §6.4.3、Atelier PHOTO-SURFACE）。
 *
 * - 0 padding、1dp `outline.subtle` 内描边、`image.canvas` 主题底；
 * - 默认 contain，不裁人物、不染色、不加装饰条；
 * - 加载失败显示独立错误态，不冒充“从未有照片”。
 *
 * 调用方负责尺寸/比例槽位；本组件不写数据库宽高。
 */
@Composable
fun CmAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (model.isNullOrBlank()) return
    val context = LocalContext.current
    val storage = LocalImageStorage.current
    val reducedMotion = LocalReducedMotion.current
    val colors = LocalAtelierColors.current
    val request = remember(model, reducedMotion) {
        ImageRequest.Builder(context)
            .data(resolveModel(storage, model))
            .crossfade(enable = !reducedMotion)
            .build()
    }

    Box(
        modifier = modifier
            .background(colors.imageCanvas)
            .border(1.dp, colors.outlineSubtle),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize().background(colors.imageCanvas))
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.imageCanvas)
                        .semantics { this.contentDescription = "图片无法读取" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.BrokenImage,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

/** 三类路径形态 → Coil 可加载对象（见组件 KDoc 的解析口径）。 */
private fun resolveModel(storage: ImageStorage, raw: String): Any = when {
    raw.startsWith("content://") || raw.startsWith("file://") -> raw.toUri()
    raw.startsWith("/") -> File(raw)
    else -> storage.toFile(raw)
        ?: File(raw) // toFile 拒绝时（路径非法）退回 File，让 Coil 报错而不是 NPE
}
