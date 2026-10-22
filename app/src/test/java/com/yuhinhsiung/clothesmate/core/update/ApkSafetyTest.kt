package com.yuhinhsiung.clothesmate.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkSafetyTest {

    private val cert = "35B37099BE2F52760E49B536AFF4A24116E88DD6655C72353F32854C698C9809"

    @Test
    fun acceptsMatchingPackageSignatureAndHigherVersion() {
        val actual = ApkMetadata("com.yuhinhsiung.clothesmate", 10001, setOf(cert.lowercase()))
        assertEquals(ApkVerificationResult.Ok, ApkSafety.verify(PKG, cert, 10000, actual))
    }

    @Test
    fun rejectsDifferentPackage() {
        val actual = ApkMetadata("com.example.other", 10001, setOf(cert))
        assertEquals(ApkVerificationResult.PackageMismatch, ApkSafety.verify(PKG, cert, 10000, actual))
    }

    @Test
    fun rejectsDifferentSignature() {
        val actual = ApkMetadata(PKG, 10001, setOf("00".repeat(32)))
        assertEquals(ApkVerificationResult.SignatureMismatch, ApkSafety.verify(PKG, cert, 10000, actual))
    }

    @Test
    fun rejectsDowngradeAndSameVersion() {
        assertEquals(
            ApkVerificationResult.NotAnUpgrade,
            ApkSafety.verify(PKG, cert, 10000, ApkMetadata(PKG, 10000, setOf(cert))),
        )
        assertEquals(
            ApkVerificationResult.NotAnUpgrade,
            ApkSafety.verify(PKG, cert, 10000, ApkMetadata(PKG, 9999, setOf(cert))),
        )
    }

    @Test
    fun rejectsMissingOrInvalidApk() {
        assertEquals(
            ApkVerificationResult.MissingSignature,
            ApkSafety.verify(PKG, cert, 10000, ApkMetadata(PKG, 10001, emptySet())),
        )
        assertEquals(ApkVerificationResult.InvalidApk, ApkSafety.verify(PKG, cert, 10000, null))
    }

    @Test
    fun digestMustBeCompleteSha256() {
        assertEquals(false, updateInfo(null).hasTrustedDigest)
        assertEquals(false, updateInfo("sha256:abc").hasTrustedDigest)
        assertEquals(true, updateInfo("sha256:${"a".repeat(64)}").hasTrustedDigest)
    }

    private fun updateInfo(digest: String?) = UpdateInfo(
        version = SemanticVersion(1, 1, 0),
        tag = "v1.1.0",
        versionName = "1.1.0",
        downloadUrl = "https://github.com/o/r/releases/download/v1.1.0/app.apk",
        assetName = "app.apk",
        assetSizeBytes = 1,
        assetDigest = digest,
        releasePageUrl = "https://github.com/o/r/releases/tag/v1.1.0",
    )

    private companion object {
        const val PKG = "com.yuhinhsiung.clothesmate"
    }
}
