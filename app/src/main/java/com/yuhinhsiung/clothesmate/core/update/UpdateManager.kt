package com.yuhinhsiung.clothesmate.core.update

import com.yuhinhsiung.clothesmate.BuildConfig
import com.yuhinhsiung.clothesmate.core.di.ApplicationScope
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState
    data class NeedsInstallPermission(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String, val releasePageUrl: String) : UpdateUiState
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
    private val downloadMutex = Mutex()
    private var downloadJob: Job? = null

    init {
        // 进程死亡后不保留旧包，也不凭旧 Ready 状态安装。
        downloader.cleanupPartials()
    }

    fun checkSilently() {
        appScope.launch {
            val now = System.currentTimeMillis()
            if (!checkStore.shouldCheckSilently(now, BuildConfig.VERSION_NAME)) return@launch
            checkInternal(manual = false)
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
            checkStore.recordAttempt(
                now = System.currentTimeMillis(),
                version = BuildConfig.VERSION_NAME,
                successful = result !is UpdateCheckResult.Failure,
            )
            _state.value = when (result) {
                is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.latest.toString())
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                is UpdateCheckResult.NoApk -> UpdateUiState.Failed("当前版本没有可用的 APK", releasePage())
                is UpdateCheckResult.Failure -> UpdateUiState.Failed(
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
        downloadJob = appScope.launch {
            downloadMutex.withLock {
                val info = (_state.value as? UpdateUiState.Available)?.info ?: return@withLock
                _state.value = UpdateUiState.Downloading(info, 0L, info.assetSizeBytes)
                val job = downloader.download(info) { downloaded, total ->
                    _state.value = UpdateUiState.Downloading(info, downloaded, total)
                }
                downloadJob = null
                when (job) {
                    is DownloadResult.Success -> {
                        val verification = installer.verify(job.file)
                        if (verification == ApkVerificationResult.Ok) {
                            _state.value = UpdateUiState.ReadyToInstall(info, job.file)
                        } else {
                            downloader.delete(job.file)
                            _state.value = UpdateUiState.Failed(
                                "下载包校验未通过，已删除",
                                info.releasePageUrl,
                            )
                        }
                    }
                    DownloadResult.Cancelled -> _state.value = UpdateUiState.Available(info)
                    DownloadResult.NoTrustedDigest -> _state.value = UpdateUiState.Failed(
                        "发布资产没有可信校验值，请用浏览器下载",
                        info.releasePageUrl,
                    )
                    DownloadResult.RejectedUrl -> _state.value = UpdateUiState.Failed(
                        "更新下载地址不在白名单内",
                        info.releasePageUrl,
                    )
                    is DownloadResult.Failed -> _state.value = UpdateUiState.Failed(
                        "下载失败：${job.reason}",
                        info.releasePageUrl,
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
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
                _state.value = UpdateUiState.Failed("安装包已被清理，请重新下载", info.releasePageUrl)
            }
            InstallLaunchResult.VerificationFailed -> {
                downloader.delete(file)
                _state.value = UpdateUiState.Failed("安装包校验未通过，已删除", info.releasePageUrl)
            }
        }
    }

    fun openReleasePage() {
        installer.openBrowser(releasePage())
    }

    fun openUnknownSourcesSettings() = installer.openUnknownSourcesSettings()

    private fun releasePage(): String =
        "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
}
