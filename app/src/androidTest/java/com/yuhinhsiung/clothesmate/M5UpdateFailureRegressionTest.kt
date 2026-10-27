package com.yuhinhsiung.clothesmate

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.update.ApkDownloader
import com.yuhinhsiung.clothesmate.core.update.ApkInstaller
import com.yuhinhsiung.clothesmate.core.update.SemanticVersion
import com.yuhinhsiung.clothesmate.core.update.UpdateCheckResult
import com.yuhinhsiung.clothesmate.core.update.UpdateCheckStore
import com.yuhinhsiung.clothesmate.core.update.UpdateInfo
import com.yuhinhsiung.clothesmate.core.update.UpdateManager
import com.yuhinhsiung.clothesmate.core.update.UpdateRepository
import com.yuhinhsiung.clothesmate.core.update.UpdateUiState
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** R03/R04/R06 regressions for update failures and silent-check concurrency. */
@RunWith(AndroidJUnit4::class)
class M5UpdateFailureRegressionTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before fun before() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        root = File(app.cacheDir, "m5-update-regression-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getCacheDir(): File = root }
    }

    @After fun after() = runBlocking {
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        root.deleteRecursively()
        Unit
    }

    private fun scope(handler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }) =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler).also { scopes += it }

    private fun store(): UpdateCheckStore {
        val file = File(root, "check-${System.nanoTime()}.preferences_pb")
        return UpdateCheckStore(PreferenceDataStoreFactory.create(scope = scope(), produceFile = { file }))
    }

    private fun info(): UpdateInfo = UpdateInfo(
        SemanticVersion(2, 0, 0), "v2.0.0", "2.0.0",
        "https://github.com/o/r/releases/download/v2.0.0/clothesmate-2.0.0.apk",
        "clothesmate-2.0.0.apk", 2L,
        "sha256:" + MessageDigest.getInstance("SHA-256").digest("ab".toByteArray()).joinToString("") { "%02x".format(it) },
        "https://github.com/o/r/releases/tag/v2.0.0",
    )

    private fun repository(block: suspend () -> UpdateCheckResult) = object : UpdateRepository {
        override suspend fun check(currentVersionName: String): UpdateCheckResult = block()
    }

    private fun manager(
        repo: UpdateRepository,
        checkStore: UpdateCheckStore = store(),
        appScope: CoroutineScope = scope(),
        client: OkHttpClient = OkHttpClient(),
    ) = UpdateManager(repo, ApkDownloader(client, context), ApkInstaller(context), checkStore, appScope)

    @Test fun cancellationMustNotPublishOldProgress() = runBlocking {
        val secondRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val reads = AtomicInteger()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = when (reads.getAndIncrement()) {
                0 -> { sink.writeUtf8("a"); 1L }
                1 -> {
                    secondRead.countDown()
                    check(releaseRead.await(10, TimeUnit.SECONDS))
                    sink.writeUtf8("b"); 1L
                }
                else -> -1L
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }
        val body = object : ResponseBody() {
            private val buffered = source.buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 2L
            override fun source(): BufferedSource = buffered
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()
        val m = manager(repository { UpdateCheckResult.Available(info()) }, client = client)
        m.checkManually()
        withTimeout(5000) { m.state.first { it is UpdateUiState.Available } }
        m.download()
        try {
            assertTrue("second read not reached", secondRead.await(10, TimeUnit.SECONDS))
            val field = UpdateManager::class.java.getDeclaredField("downloadJob").apply { isAccessible = true }
            val oldJob = field.get(m) as Job
            m.cancelDownload()
            assertTrue("cancel must immediately show Available", m.state.value is UpdateUiState.Available)
            releaseRead.countDown()
            withTimeout(5000) { oldJob.join() }
            assertTrue("cancelled read overwrote Available: ${m.state.value}", m.state.value is UpdateUiState.Available)
        } finally { releaseRead.countDown() }
    }

    @Test fun cacheWriteFailureMustNotEscapeUpdateCheck() = runBlocking {
        val escaped = CompletableDeferred<Throwable>()
        val fake = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("synthetic cache write failure")
        }
        val appScope = scope(CoroutineExceptionHandler { _, e -> escaped.complete(e) })
        val m = manager(repository { UpdateCheckResult.UpToDate(SemanticVersion(1, 0, 4)) }, UpdateCheckStore(fake), appScope)
        m.checkManually()
        val unhandled = withTimeoutOrNull(1500) { escaped.await() }
        assertNull("auxiliary update cache exception reached uncaught handler: $unhandled", unhandled)
    }

    @Test fun overlappingSilentChecksMustShareOneRequest() = runBlocking {
        val count = AtomicInteger()
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val readCount = AtomicInteger()
        val secondPassed = CompletableDeferred<Unit>()
        val prefs = MutableStateFlow<Preferences>(emptyPreferences())
        val raw = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (readCount.incrementAndGet() == 2) secondPassed.complete(Unit)
                emit(prefs.value)
            }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(prefs.value).also { prefs.value = it }
        }
        val m = manager(repository {
            if (count.incrementAndGet() == 1) { firstEntered.complete(Unit); release.await() }
            UpdateCheckResult.UpToDate(SemanticVersion(1, 0, 4))
        }, UpdateCheckStore(raw))
        m.checkSilently()
        withTimeout(5000) { firstEntered.await() }
        m.checkSilently()
        withTimeout(5000) { secondPassed.await() }
        delay(150)
        release.complete(Unit)
        withTimeout(5000) { m.state.first { it is UpdateUiState.UpToDate } }
        delay(250)
        assertEquals("overlapping silent checks issued duplicate requests", 1, count.get())
    }
}
