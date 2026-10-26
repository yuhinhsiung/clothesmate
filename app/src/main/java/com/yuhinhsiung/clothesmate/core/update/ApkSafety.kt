package com.yuhinhsiung.clothesmate.core.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class ApkMetadata(
    val packageName: String,
    val versionCode: Long,
    val certificateSha256: Set<String>,
)

sealed interface ApkVerificationResult {
    data object Ok : ApkVerificationResult
    data object PackageMismatch : ApkVerificationResult
    data object SignatureMismatch : ApkVerificationResult
    data object NotAnUpgrade : ApkVerificationResult
    data object MissingSignature : ApkVerificationResult
    data object InvalidApk : ApkVerificationResult
}

object ApkSafety {
    fun verify(
        expectedPackage: String,
        expectedCertificateSha256: String,
        currentVersionCode: Long,
        actual: ApkMetadata?,
    ): ApkVerificationResult {
        if (actual == null) return ApkVerificationResult.InvalidApk
        if (actual.packageName != expectedPackage) return ApkVerificationResult.PackageMismatch
        if (actual.certificateSha256.isEmpty()) return ApkVerificationResult.MissingSignature
        val expected = expectedCertificateSha256.filter(Char::isLetterOrDigit).uppercase()
        val actualCerts = actual.certificateSha256.map { it.filter(Char::isLetterOrDigit).uppercase() }.toSet()
        if (expected.isBlank() || expected !in actualCerts) return ApkVerificationResult.SignatureMismatch
        if (actual.versionCode <= currentVersionCode) return ApkVerificationResult.NotAnUpgrade
        return ApkVerificationResult.Ok
    }
}

class ApkMetadataReader(private val packageManager: PackageManager) {

    @Suppress("DEPRECATION")
    fun read(file: File): ApkMetadata? {
        if (!file.isFile || file.length() == 0L) return null
        // API 28/29 do not populate signingInfo for archive APKs; keep GET_SIGNATURES as
        // a fallback so signed APKs are not misclassified as MissingSignature.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info: PackageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val signatures = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAll(info.signingInfo?.apkContentsSigners.orEmpty())
            }
            if (isEmpty()) addAll(info.signatures.orEmpty())
        }
        val certs = signatures.mapNotNull { signature ->
            runCatching {
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { "%02X".format(it) }
            }.getOrNull()
        }.toSet()
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return ApkMetadata(
            packageName = info.packageName,
            versionCode = versionCode,
            certificateSha256 = certs,
        )
    }
}
