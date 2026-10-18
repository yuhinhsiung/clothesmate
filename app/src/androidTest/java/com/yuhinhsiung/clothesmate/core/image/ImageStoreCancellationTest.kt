package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R08：原图压缩**阻塞期间**页面退出，压缩仍可能写出 JPG；此时调用方已取消、
 * 结果不会再交给 ViewModel，存储层必须自己删掉这个无人接管的产物。
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreCancellationTest {

    @Test
    fun canceledStashDeletesLateWrittenTarget() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val started = CompletableDeferred<File>()
        val release = CompletableDeferred<Unit>()
        val compressor = object : ImageCompressor {
            override suspend fun compress(source: Source, target: File): Long =
                // 模拟不可中断的 Bitmap/JPEG 阻塞：即使外层已取消也先写完
                withContext(NonCancellable) {
                    started.complete(target)
                    release.await()
                    target.writeBytes(byteArrayOf(1, 2, 3))
                    target.length()
                }
        }
        val storage = DefaultImageStorage(
            File(context.cacheDir, "cancel-images-${System.nanoTime()}"),
            Dispatchers.IO,
        )
        val store = DefaultImageStore(context, compressor, storage, Dispatchers.IO)
        val job = launch(Dispatchers.Default) {
            store.stash(Uri.parse("file:///does-not-matter.jpg"))
        }
        val target = started.await()
        // 先取消（不 join，否则 NonCancellable 的压缩会阻塞 join），再放行晚写
        job.cancel()
        release.complete(Unit)
        job.join()
        assertFalse("取消后不得遗留晚完成的暂存 JPG：${target.path}", target.exists())
        // 只断言本次目标；cache 里可能有其他正在使用的草稿，不能整目录断言
        assertTrue("目标文件应已删除", !target.exists())
    }
}
