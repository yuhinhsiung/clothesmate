package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * R01：暂存文件名必须**跨进程唯一**。
 *
 * 用两个真实的 DefaultImageStore 实例模拟「进程重建」——M2 的草稿会跨进程保留
 * `staged_*.jpg` / `cutout_*.png` 路径，进程内计数器从 0 重来会复用同名文件并覆盖
 * 仍在草稿里的图片。
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreStagingNamesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val created = mutableListOf<File>()

    @After
    fun tearDown() {
        created.forEach { it.delete() }
    }

    @Test
    fun twoInstancesProduceDistinctCutoutPathsAndKeepBothFiles() = runBlocking {
        val root = File(context.cacheDir, "images-test-${UUID.randomUUID()}")
        val storage = DefaultImageStorage(root, Dispatchers.IO)
        val compressor = DefaultImageCompressor(context, Dispatchers.IO)
        val first = DefaultImageStore(context, compressor, storage, Dispatchers.IO)
        val second = DefaultImageStore(context, compressor, storage, Dispatchers.IO)

        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val a = first.stageCutout(bitmap)
        val b = second.stageCutout(bitmap)
        created += a.file
        created += b.file
        bitmap.recycle()

        assertNotEquals("两个进程实例不得复用抠图路径", a.file.path, b.file.path)
        assertTrue("第一份抠图应保留", a.file.exists() && a.file.length() > 0)
        assertTrue("第二份抠图应保留", b.file.exists() && b.file.length() > 0)
        runCatching { root.deleteRecursively() }
        Unit
    }

    @Test
    fun twoInstancesProduceDistinctOriginalStashPaths() = runBlocking {
        val root = File(context.cacheDir, "images-test-${UUID.randomUUID()}")
        val storage = DefaultImageStorage(root, Dispatchers.IO)
        val compressor = DefaultImageCompressor(context, Dispatchers.IO)
        val source = File(context.cacheDir, "staging-source-${UUID.randomUUID()}.jpg").apply {
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE)
                compress(Bitmap.CompressFormat.JPEG, 90, outputStream())
                recycle()
            }
        }
        created += source
        val first = DefaultImageStore(context, compressor, storage, Dispatchers.IO)
        val second = DefaultImageStore(context, compressor, storage, Dispatchers.IO)

        val a = first.stash(Uri.fromFile(source))
        val b = second.stash(Uri.fromFile(source))
        created += a.file
        created += b.file

        assertNotEquals("两个进程实例不得复用原图暂存路径", a.file.path, b.file.path)
        assertTrue("第一份原图应保留", a.file.exists() && a.file.length() > 0)
        assertTrue("第二份原图应保留", b.file.exists() && b.file.length() > 0)
        runCatching { root.deleteRecursively() }
        Unit
    }
}
