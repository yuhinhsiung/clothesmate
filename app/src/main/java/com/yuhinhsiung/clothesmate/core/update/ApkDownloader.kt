package com.yuhinhsiung.clothesmate.core.update

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data object NoTrustedDigest : DownloadResult
    data object RejectedUrl : DownloadResult
    data class Failed(val reason: String) : DownloadResult
    data object Cancelled : DownloadResult
}

/**
 * 更新包下载器：独立时限、逐跳 HTTPS/域名白名单、边下边算 SHA-256。
 *
 * 不复用 AI 的 15s callTimeout；只允许可信 digest 进入应用内安装。
 */
@Singleton
class ApkDownloader @Inject constructor(
    baseClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun directory(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onCall: (Call) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!info.hasTrustedDigest) return@withContext DownloadResult.NoTrustedDigest
        val expected = info.assetDigest!!.removePrefix("sha256:").lowercase()
        var url = info.downloadUrl
        var redirects = 0
        try {
            while (true) {
                if (!isAllowed(url)) return@withContext DownloadResult.RejectedUrl
                val request = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
                val call = client.newCall(request)
                onCall(call)
                call.execute().use { response ->
                    if (response.isRedirect) {
                        if (redirects++ >= MAX_REDIRECTS) return@withContext DownloadResult.RejectedUrl
                        val location = response.header("Location")
                            ?: return@withContext DownloadResult.Failed("redirect without location")
                        url = URI(url).resolve(location).toString()
                        return@use
                    }
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult.Failed("HTTP ${response.code}")
                    }
                    val body = response.body ?: return@withContext DownloadResult.Failed("empty body")
                    val total = if (info.assetSizeBytes > 0) info.assetSizeBytes else body.contentLength()
                    val safeName = info.assetName.substringAfterLast('/')
                        .replace("..", "_")
                        .ifBlank { "update.apk" }
                    val part = File(directory(), "$safeName.part")
                    val finalFile = File(directory(), safeName)
                    part.delete()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                // R04：取消可能发生在阻塞 read 期间；读完必须再查一次活跃性，
                                // 不能在已取消的协程里继续发布进度或落盘。
                                currentCoroutineContext().ensureActive()
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                downloaded += read
                                if (info.assetSizeBytes > 0 && downloaded > info.assetSizeBytes) {
                                    throw IOException("size exceeds asset metadata")
                                }
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actual != expected) {
                        part.delete()
                        return@withContext DownloadResult.Failed("sha256 mismatch")
                    }
                    if (info.assetSizeBytes > 0 && downloaded != info.assetSizeBytes) {
                        part.delete()
                        return@withContext DownloadResult.Failed("size mismatch")
                    }
                    finalFile.delete()
                    if (!part.renameTo(finalFile)) {
                        part.copyTo(finalFile, overwrite = true)
                        part.delete()
                    }
                    return@withContext DownloadResult.Success(finalFile)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            DownloadResult.Failed("unreachable")
        } catch (cancelled: CancellationException) {
            cleanupPartials()
            DownloadResult.Cancelled
        } catch (failure: Exception) {
            cleanupPartials()
            DownloadResult.Failed(failure.message ?: "download failed")
        }
    }

    fun cleanupPartials() {
        directory().listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) file.delete()
        }
    }

    fun delete(file: File?) {
        file?.delete()
    }

    private fun isAllowed(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme.equals("https", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.lowercase() in ALLOWED_HOSTS
    }.getOrDefault(false)

    private companion object {
        const val MAX_REDIRECTS = 5
        val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
    }
}
