package com.yuhinhsiung.clothesmate.core.update

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UpdateRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: UpdateRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = DefaultUpdateRepository(retrofit.create(GitHubReleaseApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun availableWhenTagAssetAndDigestAreValid() = runBlocking {
        server.enqueue(jsonResponse(releaseJson(tag = "v1.1.0", assetName = "clothesmate-1.1.0.apk", digest = DIGEST)))
        val result = repository.check("1.0.0")
        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).info
        assertEquals("1.1.0", info.versionName)
        assertEquals("sha256:$DIGEST", info.assetDigest)
        assertTrue(info.hasTrustedDigest)
    }

    @Test
    fun upToDateDoesNotOfferInstall() = runBlocking {
        server.enqueue(jsonResponse(releaseJson(tag = "v1.0.0", assetName = "clothesmate-1.0.0.apk", digest = DIGEST)))
        assertTrue(repository.check("1.0.0") is UpdateCheckResult.UpToDate)
    }

    @Test
    fun noApkWhenOnlyDebugAssetExists() = runBlocking {
        server.enqueue(jsonResponse(releaseJson(tag = "v1.1.0", assetName = "clothesmate-1.1.0-debug.apk", digest = DIGEST)))
        assertEquals(NoApkReason.NO_APK, (repository.check("1.0.0") as UpdateCheckResult.NoApk).reason)
    }

    @Test
    fun multipleApksAreRejectedAsAmbiguous() = runBlocking {
        server.enqueue(jsonResponse(releaseJson(tag = "v1.1.0", extraApk = "clothesmate-1.1.0-arm64.apk", digest = DIGEST)))
        assertEquals(NoApkReason.AMBIGUOUS_APK, (repository.check("1.0.0") as UpdateCheckResult.NoApk).reason)
    }

    @Test
    fun tagAndAssetMismatchIsRejected() = runBlocking {
        server.enqueue(jsonResponse(releaseJson(tag = "v1.1.0", assetName = "clothesmate-1.0.9.apk", digest = DIGEST)))
        assertEquals(NoApkReason.TAG_ASSET_MISMATCH, (repository.check("1.0.0") as UpdateCheckResult.NoApk).reason)
    }

    @Test
    fun httpErrorIsFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(UpdateFailure.HTTP, (repository.check("1.0.0") as UpdateCheckResult.Failure).reason)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun releaseJson(
        tag: String,
        assetName: String = "clothesmate-1.1.0.apk",
        extraApk: String? = null,
        digest: String? = null,
    ): String {
        val apk = { name: String ->
            """{"name":"$name","size":1234,"browser_download_url":"https://github.com/o/r/releases/download/$tag/$name","digest":${digest?.let { "\"sha256:$it\"" } ?: "null"}}"""
        }
        val assets = buildList {
            add(apk(assetName))
            extraApk?.let { add(apk(it)) }
        }.joinToString(",")
        return """{"tag_name":"$tag","name":"$tag","html_url":"https://github.com/o/r/releases/tag/$tag","draft":false,"prerelease":false,"assets":[$assets]}"""
    }

    private companion object {
        const val DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
