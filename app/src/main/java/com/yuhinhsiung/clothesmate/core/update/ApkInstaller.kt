package com.yuhinhsiung.clothesmate.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.yuhinhsiung.clothesmate.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InstallLaunchResult {
    data object Launched : InstallLaunchResult
    data object NeedsUnknownSourcesPermission : InstallLaunchResult
    data object FileMissing : InstallLaunchResult
    data object VerificationFailed : InstallLaunchResult
}

@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val metadataReader = ApkMetadataReader(context.packageManager)

    fun verify(file: File): ApkVerificationResult {
        if (!file.isFile || file.length() == 0L) return ApkVerificationResult.InvalidApk
        val currentVersion = currentVersionCode()
        return ApkSafety.verify(
            expectedPackage = BuildConfig.APPLICATION_ID,
            expectedCertificateSha256 = BuildConfig.RELEASE_CERT_SHA256,
            currentVersionCode = currentVersion,
            actual = metadataReader.read(file),
        )
    }

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun launch(file: File): InstallLaunchResult {
        if (!file.isFile) return InstallLaunchResult.FileMissing
        if (verify(file) != ApkVerificationResult.Ok) {
            return InstallLaunchResult.VerificationFailed
        }
        if (!canRequestPackageInstalls()) return InstallLaunchResult.NeedsUnknownSourcesPermission
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return InstallLaunchResult.Launched
    }

    fun openUnknownSourcesSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openBrowser(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }
}
