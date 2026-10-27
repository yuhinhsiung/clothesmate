package com.yuhinhsiung.clothesmate.core.update

import android.util.Log
import com.yuhinhsiung.clothesmate.BuildConfig
import com.yuhinhsiung.clothesmate.core.di.ApplicationScope
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call

enum class UpdateFailureStage {
    CHECK,
    DOWNLOAD,
    VERIFY,
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState
    data class NeedsInstallPermission(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(
        val stage: UpdateFailureStage,
        val message: String,
        val releasePageUrl: String,
    ) : UpdateUiState
}

/**
 * 启动静默检查 + 设置页手动检查合流到同一状态机；下载/安装都是 single-flight。
 *
 * 进程死亡后不保留 ReadyToInstall：状态只在内存，重启由 init 清理半成品。
 */
@Singleton
class UpdateManager @Inject constructor(
    private val repository: UpdateRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val checkStore: UpdateCheckStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val checkMutex = Mutex()
    private val silentGate = AtomicBoolean(false)
    private val downloadMutex = Mutex()
    private var downloadJob: Job? = null
    private var downloadSession: Any? = null
    @Volatile private var activeCall: Call? = null

    init {
        // 进程死亡后不保留旧包，也不凭旧 Ready 状态安装。
        downloader.cleanupPartials()
    }

    fun checkSilently() {
        appScope.launch {
            val now = System.currentTimeMillis()
            val shouldCheck = runCatching {
                checkStore.shouldCheckSilently(now, BuildConfig.VERSION_NAME)
            }.getOrDefault(false)
            if (!shouldCheck) return@launch
            // R06：TTL 读取在锁外，但用 CAS 抢占，旋转/重建不会把同一轮静默检查
            // 排成第二次网络请求。抢占失败说明已有静默检查在途，直接复用其结果。
            if (!silentGate.compareAndSet(false, true)) return@launch
            try {
                checkInternal(manual = false)
            } finally {
                silentGate.set(false)
            }
        }
    }

    fun checkManually() {
        appScope.launch { checkInternal(manual = true) }
    }

    private suspend fun checkInternal(manual: Boolean) {
        checkMutex.withLock {
            if (_state.value is UpdateUiState.Downloading) return
            _state.value = UpdateUiState.Checking
            val result = try {
                repository.check(BuildConfig.VERSION_NAME)
            } catch (failure: Exception) {
                UpdateCheckResult.Failure(UpdateFailure.NETWORK)
            }
            // R03：节流时间是辅助缓存，DataStore 落盘失败不能把一次可用的检查
            // 变成应用级未捕获异常；状态仍要发布给 UI。
            runCatching {
                checkStore.recordAttempt(
                    now = System.currentTimeMillis(),
                    version = BuildConfig.VERSION_NAME,
                    successful = result !is UpdateCheckResult.Failure,
                )
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w("UpdateManager", "update check cache write failed", it)
            }
            _state.value = when (result) {
                is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.latest.toString())
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                is UpdateCheckResult.NoApk -> UpdateUiState.Failed(
                    UpdateFailureStage.CHECK,
                    "当前版本没有可用的 APK",
                    releasePage(),
                )
                is UpdateCheckResult.Failure -> UpdateUiState.Failed(
                    UpdateFailureStage.CHECK,
                    when (result.reason) {
                        UpdateFailure.HTTP -> "更新服务暂时不可用"
                        UpdateFailure.NETWORK -> "网络不可用，请稍后重试"
                        UpdateFailure.INVALID_RESPONSE -> "更新信息格式异常"
                    },
                    releasePage(),
                )
            }
        }
    }

    fun download() {
        if (downloadJob?.isActive == true) return
        val session = Any()
        downloadSession = session
        activeCall = null
        val job = appScope.launch {
            downloadMutex.withLock {
                val info = (_state.value as? UpdateUiState.Available)?.info ?: return@withLock
                if (downloadSession !== session) return@withLock
                _state.value = UpdateUiState.Downloading(info, 0L, info.assetSizeBytes)
                val job = downloader.download(
                    info = info,
                    // R04：旧读线程即使晚到，也不能把已取消/新会话的状态改回 Downloading。
                    onProgress = { downloaded, total ->
                        if (downloadSession === session && _state.value is UpdateUiState.Downloading) {
                            _state.value = UpdateUiState.Downloading(info, downloaded, total)
                        }
                    },
                    onCall = { call ->
                        if (downloadSession === session) activeCall = call else call.cancel()
                    },
                )
                if (downloadSession !== session) return@withLock
                downloadSession = null
                activeCall = null
                downloadJob = null
                when (job) {
                    is DownloadResult.Success -> {
                        val verification = installer.verify(job.file)
                        if (verification == ApkVerificationResult.Ok) {
                            _state.value = UpdateUiState.ReadyToInstall(info, job.file)
                        } else {
                            downloader.delete(job.file)
                            _state.value = UpdateUiState.Failed(
                                UpdateFailureStage.VERIFY,
                                "下载包校验未通过，已删除",
                                info.releasePageUrl,
                            )
                        }
                    }
                    DownloadResult.Cancelled -> _state.value = UpdateUiState.Available(info)
                    DownloadResult.NoTrustedDigest -> _state.value = UpdateUiState.Failed(
                        UpdateFailureStage.DOWNLOAD,
                        "发布资产没有可信校验值，请用浏览器下载",
                        info.releasePageUrl,
                    )
                    DownloadResult.RejectedUrl -> _state.value = UpdateUiState.Failed(
                        UpdateFailureStage.DOWNLOAD,
                        "更新下载地址不在白名单内",
                        info.releasePageUrl,
                    )
                    is DownloadResult.Failed -> {
                        Log.w("UpdateManager", "download failed: ${job.reason}")
                        _state.value = UpdateUiState.Failed(
                            UpdateFailureStage.DOWNLOAD,
                            downloadFailureText(job.reason),
                            info.releasePageUrl,
                        )
                    }
                }
            }
        }
        downloadJob = job
    }

    fun cancelDownload() {
        val job = downloadJob ?: return
        // 先失效会话并取消真实 OkHttp Call，再取消协程；阻塞 read 会立即退出，
        // 且晚到的进度回调会被会话检查丢弃。
        downloadSession = null
        activeCall?.cancel()
        activeCall = null
        job.cancel()
        downloadJob = null
        val current = _state.value
        if (current is UpdateUiState.Downloading) {
            downloader.cleanupPartials()
            _state.value = UpdateUiState.Available(current.info)
        }
    }

    fun install() {
        val current = _state.value
        val info = when (current) {
            is UpdateUiState.ReadyToInstall -> current.info
            is UpdateUiState.NeedsInstallPermission -> current.info
            else -> return
        }
        val file = when (current) {
            is UpdateUiState.ReadyToInstall -> current.file
            is UpdateUiState.NeedsInstallPermission -> current.file
            else -> return
        }
        when (val result = installer.launch(file)) {
            InstallLaunchResult.Launched -> Unit
            InstallLaunchResult.NeedsUnknownSourcesPermission -> {
                _state.value = UpdateUiState.NeedsInstallPermission(info, file)
                installer.openUnknownSourcesSettings()
            }
            InstallLaunchResult.FileMissing -> {
                downloader.delete(file)
                _state.value = UpdateUiState.Failed(
                    UpdateFailureStage.DOWNLOAD,
                    "安装包已被清理，请重新下载",
                    info.releasePageUrl,
                )
            }
            InstallLaunchResult.VerificationFailed -> {
                downloader.delete(file)
                _state.value = UpdateUiState.Failed(
                    UpdateFailureStage.VERIFY,
                    "安装包校验未通过，已删除",
                    info.releasePageUrl,
                )
            }
        }
    }

    fun openReleasePage() {
        installer.openBrowser(releasePage())
    }

    fun openUnknownSourcesSettings() = installer.openUnknownSourcesSettings()

    /** 用户从系统「安装未知应用」页返回后，把已授权的状态从 NeedsInstallPermission 推进到可安装。 */
    fun refreshInstallPermission() {
        val current = _state.value as? UpdateUiState.NeedsInstallPermission ?: return
        if (installer.canRequestPackageInstalls()) {
            _state.value = UpdateUiState.ReadyToInstall(current.info, current.file)
        }
    }

    private fun releasePage(): String =
        "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
}
