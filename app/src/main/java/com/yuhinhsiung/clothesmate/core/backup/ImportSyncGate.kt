package com.yuhinhsiung.clothesmate.core.backup

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.yuhinhsiung.clothesmate.BuildConfig
import java.io.File

/**
 * 仅 debug 包启用的导入同步点。
 *
 * 在 `files/import-sync-gate` 写入点名后，导入会停在对应同步点，等待外部删除该文件；
 * 用于 M5-15 的真进程 kill 演练。release 包 BuildConfig.DEBUG=false，直接返回。
 */
internal object ImportSyncGate {
    private const val FILE_NAME = "import-sync-gate"
    private const val TAG = "ImportSync"
    private const val TIMEOUT_MS = 120_000L

    fun pause(context: Context, point: String) {
        if (!BuildConfig.DEBUG) return
        val gate = File(context.filesDir, FILE_NAME)
        if (!gate.isFile) return
        val wanted = runCatching { gate.readText().trim() }.getOrNull()
        if (wanted != point) return
        Log.i(TAG, "PAUSED:$point")
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (gate.isFile && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100)
        }
        Log.i(TAG, "RESUMED:$point")
    }
}
