package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R03：抠图超时必须转成 [BackgroundRemovalException]，不能以
 * TimeoutCancellationException（CancellationException）冒到预览页，
 * 否则页面会把它当外部取消、永远停在「处理中」。
 */
@RunWith(AndroidJUnit4::class)
class TfliteCutoutTimeoutTest {

    @Test
    fun timeoutBecomesBackgroundRemovalException() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val slowLoader = object : BitmapLoader {
            private val delegate = DefaultBitmapLoader(context, Dispatchers.IO)
            override suspend fun decode(uri: Uri, maxEdge: Int): Bitmap {
                delay(2_000)
                return delegate.decode(uri, maxEdge)
            }

            override suspend fun decode(file: File, maxEdge: Int): Bitmap = decode(Uri.fromFile(file), maxEdge)
            override suspend fun jpegBytes(uri: Uri, maxEdge: Int, quality: Int): ByteArray =
                delegate.jpegBytes(uri, maxEdge, quality)
            override suspend fun jpegBytes(file: File, maxEdge: Int, quality: Int): ByteArray =
                delegate.jpegBytes(file, maxEdge, quality)
        }
        val remover = TfliteU2NetBackgroundRemover(context, slowLoader, Dispatchers.IO)
        val source = File(context.cacheDir, "timeout-source.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val failure = runCatching {
                remover.removeBackground(Uri.fromFile(source), timeoutMillis = 100)
            }.exceptionOrNull()
            assertTrue(
                "超时应是 BackgroundRemovalException，实际 ${failure?.javaClass?.name}",
                failure is BackgroundRemovalException,
            )
            assertTrue("失败信息应说明超时：${failure?.message}", failure?.message?.contains("超时") == true)
        } finally {
            source.delete()
        }
    }
}
