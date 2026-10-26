package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M5-09：落盘失败（权限/空间不足的等价注入）必须抛出可识别异常，且暂存源保留，
 * 用户可原地重试。只在 androidTest 构造失败条件，不在正式包留注入面。
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreWriteFailureTest {

    @Test
    fun promotionFailureKeepsStagedSourceForRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "m509-images").apply { deleteRecursively(); mkdirs() }
        val store = DefaultImageStore(
            context = context,
            compressor = DefaultImageCompressor(context, Dispatchers.IO),
            storage = DefaultImageStorage(root, Dispatchers.IO),
            ioDispatcher = Dispatchers.IO,
        )
        val stagedFile = File(context.cacheDir, "m509-staged.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        val lockedDir = File(root, "locked").apply {
            mkdirs()
            setWritable(false)
        }
        val destination = File(lockedDir, "out.jpg")
        try {
            store.promote(StagedImage(stagedFile, stagedFile.length()), destination)
            fail("写入不可写目录应失败")
        } catch (expected: Exception) {
            // 保存流程 catch Throwable 后显示 error.message；这里只要求失败可观察，
            // 不把异常类型写死（Android 文件系统在不同 API 上抛出的具体类型不同）。
            assertTrue("失败必须带可读信息", !expected.message.isNullOrBlank())
        } finally {
            lockedDir.setWritable(true)
        }
        assertTrue("失败后暂存源必须保留，草稿才能重试", stagedFile.exists())
        assertFalse("失败后不得留下目标文件", destination.exists())
    }
}
