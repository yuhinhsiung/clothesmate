package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * M6-04：只读图片头 + EXIF 的宽高旁路缓存。
 *
 * - 不解码整张大图（`inJustDecodeBounds`）；
 * - 缓存键含相对路径/URI、文件长度、最后修改时间与算法版本；
 * - 可随时删除重建，不写 Room；导入/替换后旧键自然失效。
 */
data class PhotoDimensions(val width: Int, val height: Int) {
    /** 宽/高，已按 EXIF 方向校正；非法尺寸返回 3:4 兜底。 */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0.75f
}

object PhotoDimensionsReader {
    private const val VERSION = 1
    private const val FILE_NAME = "photo-dimensions-v1.json"
    private val memory = ConcurrentHashMap<String, PhotoDimensions>()
    private var loaded = false

    fun cached(context: Context, model: String?): PhotoDimensions? {
        val key = cacheKey(context, model) ?: return null
        if (!loaded) load(context)
        return memory[key]
    }

    suspend fun read(context: Context, model: String?): PhotoDimensions? = withContext(Dispatchers.IO) {
        val key = cacheKey(context, model) ?: return@withContext null
        if (!loaded) load(context)
        memory[key]?.let { return@withContext it }
        val dims = decode(context, model) ?: return@withContext null
        memory[key] = dims
        persist(context)
        dims
    }

    private fun cacheKey(context: Context, model: String?): String? {
        if (model.isNullOrBlank()) return null
        return when {
            model.startsWith("content://") || model.startsWith("file://") -> {
                val uri = Uri.parse(model)
                val size = runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                }.getOrNull() ?: -1L
                "$model|$size|$VERSION"
            }
            else -> {
                val file = if (model.startsWith("/")) File(model) else LocalImageRoot.file(context, model)
                if (!file.isFile) return null
                "$model|${file.length()}|${file.lastModified()}|$VERSION"
            }
        }
    }

    private fun decode(context: Context, model: String?): PhotoDimensions? {
        if (model.isNullOrBlank()) return null
        return if (model.startsWith("content://") || model.startsWith("file://")) {
            val uri = Uri.parse(model)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } }
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            oriented(options.outWidth, options.outHeight, orientation)
        } else {
            val file = if (model.startsWith("/")) File(model) else LocalImageRoot.file(context, model)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            val orientation = runCatching {
                ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            oriented(options.outWidth, options.outHeight, orientation)
        }
    }

    private fun oriented(width: Int, height: Int, orientation: Int): PhotoDimensions? {
        if (width <= 0 || height <= 0) return null
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
            -> PhotoDimensions(height, width)
            else -> PhotoDimensions(width, height)
        }
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        runCatching {
            val file = File(context.cacheDir, FILE_NAME)
            if (!file.isFile) return@runCatching
            val json = JSONObject(file.readText())
            json.keys().forEach { key ->
                val obj = json.optJSONObject(key) ?: return@forEach
                val w = obj.optInt("w")
                val h = obj.optInt("h")
                if (w > 0 && h > 0) memory[key] = PhotoDimensions(w, h)
            }
        }
        loaded = true
    }

    @Synchronized
    private fun persist(context: Context) {
        runCatching {
            val json = JSONObject()
            memory.forEach { (key, dims) ->
                json.put(key, JSONObject().put("w", dims.width).put("h", dims.height))
            }
            File(context.cacheDir, FILE_NAME).writeText(json.toString())
        }
    }
}

/** 供缓存键使用的最小路径根；实际合法性仍由 ImageStorage.toFile 决定。 */
private object LocalImageRoot {
    fun file(context: Context, relativePath: String): File = File(File(context.filesDir, "images"), relativePath)
}
