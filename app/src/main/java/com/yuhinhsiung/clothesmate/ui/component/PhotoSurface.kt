package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yuhinhsiung.clothesmate.core.image.PhotoDimensionsReader

/**
 * M6 Photo Surface 比例：先取旁路缓存，未命中再 IO 读文件头；不阻塞首帧。
 *
 * 返回 null = 未知，调用方用 3:4 槽并在本次浏览内保持该槽。
 */
@Composable
fun rememberPhotoAspectRatio(model: String?): Float? {
    val context = LocalContext.current
    var ratio by remember(model) {
        mutableStateOf(PhotoDimensionsReader.cached(context, model)?.aspectRatio)
    }
    LaunchedEffect(model) {
        if (ratio == null) {
            ratio = PhotoDimensionsReader.read(context, model)?.aspectRatio
        }
    }
    return ratio
}
