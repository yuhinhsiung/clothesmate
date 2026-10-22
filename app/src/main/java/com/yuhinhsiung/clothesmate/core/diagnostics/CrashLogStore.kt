package com.yuhinhsiung.clothesmate.core.diagnostics

import android.os.Build
import com.yuhinhsiung.clothesmate.BuildConfig
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 本地崩溃日志（M5-11）。
 *
 * - 只写应用私有目录，不引入 ACRA / Play Vitals / 网络上报；
 * - 每次进程启动前安装 handler，记录时间 / 版本 / 机型 / 线程 / 脱敏堆栈；
 * - 1MB × 3 轮转；写入失败只吞掉，绝不递归、绝不阻塞主流程；
 * - 设置页「诊断日志」导出脱敏报告。
 */
class CrashLogStore(
    private val directory: File,
    private val maxFiles: Int = MAX_FILES,
    private val maxFileBytes: Int = MAX_FILE_BYTES,
) {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { record(thread, throwable) }
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun record(thread: Thread, throwable: Throwable): File? {
        return runCatching {
            val now = System.currentTimeMillis()
            val text = buildString {
                append("ClothesMate crash log\n")
                append("time=").append(timestamp(now)).append('\n')
                append("versionName=").append(BuildConfig.VERSION_NAME).append('\n')
                append("versionCode=").append(BuildConfig.VERSION_CODE).append('\n')
                append("sdk=").append(Build.VERSION.SDK_INT).append('\n')
                append("device=").append(Build.MODEL ?: "unknown").append('\n')
                append("thread=").append(thread.name).append('\n')
                append("---- stack ----\n")
                append(throwable.stackTraceToString())
                append('\n')
            }
            val redacted = CrashRedactor.redact(text)
            val bytes = redacted.toByteArray(Charsets.UTF_8)
                .let { if (it.size <= maxFileBytes) it else it.copyOf(maxFileBytes) }

            synchronized(this) {
                if (!directory.exists()) directory.mkdirs()
                pruneFor(bytes.size)
                val file = nextFile(now)
                file.writeBytes(bytes)
                file
            }
        }.getOrNull()
    }

    fun logs(): List<CrashLogFile> {
        return runCatching {
            directory.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.map { CrashLogFile(it) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * 生成导出文本。再次脱敏，兼容旧版本写下的未脱敏日志。
     * 只读前 [MAX_REPORT_CHARS] 个字符，避免超长文件拖垮导出。
     */
    fun buildReport(): String {
        val header = buildString {
            append("ClothesMate 诊断日志\n")
            append("生成时间: ").append(timestamp(System.currentTimeMillis())).append('\n')
            append("应用版本: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Android: ").append(Build.VERSION.SDK_INT).append('\n')
            append("设备: ").append(Build.MODEL ?: "unknown").append('\n')
            append("说明: 已脱敏，不含 API Key、天气私钥、图片路径或 SQL 明细。\n")
            append("----\n\n")
        }
        val files = logs()
        if (files.isEmpty()) return header + "（暂无崩溃日志）\n"
        val body = buildString {
            files.forEachIndexed { index, log ->
                if (length > MAX_REPORT_CHARS) return@forEachIndexed
                append("## ").append(index + 1).append(' ').append(log.name).append('\n')
                append(
                    runCatching {
                        CrashRedactor.redact(log.file.readText(Charsets.UTF_8))
                    }.getOrDefault("[无法读取该日志]\n"),
                )
                append("\n\n")
            }
        }
        return header + body.take(MAX_REPORT_CHARS)
    }

    private fun pruneFor(incomingBytes: Int) {
        val existing = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList()
            ?: return
        while (existing.size >= maxFiles) {
            existing.removeFirstOrNull()?.delete()
        }
        var total = existing.sumOf { it.length() }
        while (existing.isNotEmpty() && total + incomingBytes > maxFiles.toLong() * maxFileBytes) {
            val oldest = existing.removeFirstOrNull() ?: break
            total -= oldest.length()
            oldest.delete()
        }
    }

    private fun nextFile(now: Long): File {
        var candidate = File(directory, "crash-$now.log")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "crash-$now-${suffix++}.log")
        }
        return candidate
    }

    private fun timestamp(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(epochMillis))

    companion object {
        const val MAX_FILES = 3
        const val MAX_FILE_BYTES = 1_000_000
        private const val MAX_REPORT_CHARS = 1_500_000

        fun defaultDirectory(filesDir: File): File = File(filesDir, "diagnostics")
    }
}

data class CrashLogFile(val file: File) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    val modifiedAt: Long get() = file.lastModified()
}
