package com.yuhinhsiung.clothesmate.core.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M5-16d: exercise the production metadata reader and safety gate against real APK files.
 *
 * Fixtures are pushed to the app-specific external files directory by the verification script:
 * final.apk (release 1.0.0), baseline.apk (0.9.99), debug.apk (debug certificate),
 * other.apk (different package), other-unsigned.apk, tampered.apk, truncated.apk.
 */
@RunWith(AndroidJUnit4::class)
class ApkRealSampleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val reader = ApkMetadataReader(context.packageManager)
    private val externalFixtures = File(context.getExternalFilesDir(null), "update-fixtures")

    private val packageName = "com.yuhinhsiung.clothesmate"
    private val releaseCert = "35B37099BE2F52760E49B536AFF4A24116E88DD6655C72353F32854C698C9809"

    /**
     * API 36 的 FUSE 外部存储会让 `getPackageArchiveInfo` 解析失败（logcat:
     * Failure to parse package archive / fs-verity ioctl denied），而生产下载器写的是
     * 内部 cache。测试先把 fixture 复制到内部 cache，走真实路径。
     */
    private fun fixture(name: String): File {
        val dir = File(context.cacheDir, "update-fixtures").apply { mkdirs() }
        val internal = File(dir, name)
        if (!internal.exists()) {
            File(externalFixtures, name).copyTo(internal, overwrite = true)
        }
        return internal
    }

    private fun verify(name: String, versionCode: Long) = ApkSafety.verify(
        expectedPackage = packageName,
        expectedCertificateSha256 = releaseCert,
        currentVersionCode = versionCode,
        actual = reader.read(fixture(name)),
    )

    @Test
    fun fixturesArePresent() {
        assumeTrue("fixtures are pushed by the M5-16d script", externalFixtures.isDirectory)
        val expected = listOf(
            "final.apk", "baseline.apk", "debug.apk", "other.apk", "other-unsigned.apk",
            "tampered.apk", "truncated.apk",
        )
        expected.forEach { assertTrue("$it missing", File(externalFixtures, it).isFile) }
    }

    @Test
    fun finalReleasePassesAllChecks() {
        assumeTrue(externalFixtures.isDirectory)
        val metadata = reader.read(fixture("final.apk"))
        assertNotNull(metadata)
        assertEquals(packageName, metadata!!.packageName)
        assertEquals(10000L, metadata.versionCode)
        assertTrue(metadata.certificateSha256.contains(releaseCert))
        assertEquals(ApkVerificationResult.Ok, verify("final.apk", 9999L))
    }

    @Test
    fun lowerVersionIsNotAnUpgrade() {
        assumeTrue(externalFixtures.isDirectory)
        assertEquals(ApkVerificationResult.NotAnUpgrade, verify("baseline.apk", 10000L))
    }

    @Test
    fun debugSignedBuildIsRejected() {
        assumeTrue(externalFixtures.isDirectory)
        assertEquals(ApkVerificationResult.SignatureMismatch, verify("debug.apk", 9999L))
    }

    @Test
    fun differentPackageIsRejected() {
        assumeTrue(externalFixtures.isDirectory)
        assertEquals(ApkVerificationResult.PackageMismatch, verify("other.apk", 0L))
    }

    @Test
    fun unsignedAndCorruptArchivesAreRejected() {
        assumeTrue(externalFixtures.isDirectory)
        val unsigned = verify("other-unsigned.apk", 0L)
        assertTrue(unsigned == ApkVerificationResult.InvalidApk || unsigned == ApkVerificationResult.MissingSignature)
        assertEquals(ApkVerificationResult.InvalidApk, verify("truncated.apk", 0L))
        assertTrue(verify("tampered.apk", 0L) != ApkVerificationResult.Ok)
    }

    @Test
    fun writeClassificationReport() {
        assumeTrue(externalFixtures.isDirectory)
        val results = listOf(
            "final.apk" to verify("final.apk", 9999L),
            "baseline.apk" to verify("baseline.apk", 10000L),
            "debug.apk" to verify("debug.apk", 9999L),
            "other.apk" to verify("other.apk", 0L),
            "other-unsigned.apk" to verify("other-unsigned.apk", 0L),
            "tampered.apk" to verify("tampered.apk", 0L),
            "truncated.apk" to verify("truncated.apk", 0L),
        )
        File(context.getExternalFilesDir(null), "m5-16d-results.txt")
            .writeText(results.joinToString("\n") { "${it.first}=${it.second}" })
    }
}
