package com.yuhinhsiung.clothesmate

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.backup.*
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.*
import com.yuhinhsiung.clothesmate.core.datastore.*
import com.yuhinhsiung.clothesmate.core.image.*
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultWardrobeRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.WardrobeDetailViewModel
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Independent review probes. Expected business invariants, never production DB/files. */
@RunWith(AndroidJUnit4::class)
class BackupSettingsBindingTest {
    private lateinit var context: Context
    private lateinit var db: ClothesMateDatabase
    private lateinit var root: File
    private lateinit var storage: ImageStorage
    private val json = Json { encodeDefaults = true }
    private val password = "review-password"
    private val settings = InMemoryUserSettingsStore()
    private class Counter : CodeCounterStore {
        var value = 1L
        override suspend fun currentValue() = value
        override suspend fun setValue(value: Long) { this.value = value }
        override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long {
            while (existsConflict(value)) value++
            return value++
        }
    }
    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        root = File(context.cacheDir, "m4-review-${System.nanoTime()}").apply { mkdirs() }
        storage = DefaultImageStorage(File(root, "images"), Dispatchers.IO)
    }
    @After fun after() { db.close(); root.deleteRecursively() }
    private fun repo(store: UserSettingsStore = settings): DefaultBackupRepository {
        val clothing = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        return DefaultBackupRepository(context, db, db.backupDao(), db.wardrobeDao(), db.clothingItemDao(),
            db.codeHistoryDao(), db.outfitDao(), db.outfitCategoryDao(), db.wearRecordDao(),
            db.tagDictionaryDao(), storage, CodeAllocator(Counter(), clothing), store)
    }
    private fun wardrobe(id: Long = 20) = WardrobeEntity(id, "review", null, null, 0, 1)
    private fun clothing(id: Long = 30, code: String = "9", deleted: Long? = null) = ClothingItemEntity(
        id = id, code = code, wardrobeId = 20, category = "shirt", colors = listOf("white"),
        styleTags = emptyList(), seasonTags = listOf("spring"), originalImagePath = null,
        cutoutImagePath = null, brand = null, name = "review", note = null, wearCount = 4,
        deletedAt = deleted, createdAt = 1, updatedAt = 1,
    )
    private fun zip(manifest: BackupManifest, images: Map<String, ByteArray> = emptyMap()): Uri {
        val file = File(root, "${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            images.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }
    @Test fun import_failedRestoreDoesNotGrantOldConsentToNewEndpoint() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val raw = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(root, "rollback-consent.preferences_pb") })
        val cipher = object : SecretCipher {
            override fun encrypt(plainText: String) = "test-cipher:" + plainText
            override fun decrypt(cipherText: String) = cipherText.removePrefix("test-cipher:")
        }
        val real = DataStoreUserSettingsStore(raw, cipher)
        val oldAi = AiConfig("https://trusted-old.example.invalid", "old-fake-key", "old-model")
        val newAi = AiConfig("https://new.example.invalid", "new-fake-key", "new-model")
        real.saveAiConfig(oldAi)
        real.setAiConsent(AiConsent.Granted)
        real.setAiStatus(AiStatus.Verified, "old-verified", oldAi)
        val failing = object : UserSettingsStore by real {
            override suspend fun saveAiConfig(config: AiConfig) {
                if (config == oldAi) throw java.io.IOException("review injected old-config restoration failure")
                real.saveAiConfig(config)
            }
            override suspend fun setWeatherAuth(auth: com.yuhinhsiung.clothesmate.core.weather.WeatherAuthConfig) {
                if (auth.apiHost == "weather-new.example.invalid") throw java.io.IOException("review injected import weather failure")
                real.setWeatherAuth(auth)
            }
        }
        val credentials = BackupCredentials(newAi.baseUrl, newAi.apiKey, newAi.model, weatherApiHost = "weather-new.example.invalid")
        val encrypted = BackupCredentialCipher.encrypt(json.encodeToString(BackupCredentials.serializer(), credentials), password)
        val manifest = BackupManifest(exportedAt = 1, wardrobes = listOf(wardrobe()),
            settings = BackupSettings(encryptedCredentials = encrypted))
        try {
            val result = runCatching { repo(failing).import(zip(manifest), password) }
            assertTrue(result.isFailure)
            assertEquals(0, db.backupDao().wardrobes().size)
            val current = real.settings.first()
            assertEquals(newAi, current.aiConfig)
            assertNotEquals("Old endpoint consent must not be transferred to an unrestored new endpoint", AiConsent.Granted, current.aiConsent)
            assertNotEquals("Old endpoint verification must not be transferred to the new endpoint", AiStatus.Verified, current.aiStatus)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun concurrentConfigChangeBeforeConsentCommitMustNotReceiveOldGrant() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val raw = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(root, "concurrent-${System.nanoTime()}.preferences_pb") })
        val cipher = object : SecretCipher {
            override fun encrypt(plainText: String) = "test-cipher:" + plainText
            override fun decrypt(cipherText: String) = cipherText.removePrefix("test-cipher:")
        }
        val real = DataStoreUserSettingsStore(raw, cipher)
        val oldAi = AiConfig("https://trusted-old.example.invalid", "old-key", "old-model")
        val importedAi = AiConfig("https://backup-new.example.invalid", "new-key", "new-model")
        val concurrentAi = AiConfig("https://edited-third.example.invalid", "third-key", "third-model")
        val atConsentWrite = CompletableDeferred<Unit>()
        val continueConsent = CompletableDeferred<Unit>()
        try {
            real.saveAiConfig(oldAi)
            real.setAiConsent(AiConsent.Granted)
            real.setAiStatus(AiStatus.Verified, "old-verified", oldAi)
            val failing = object : UserSettingsStore by real {
                override suspend fun setWeatherAuth(auth: com.yuhinhsiung.clothesmate.core.weather.WeatherAuthConfig) {
                    if (auth.apiHost == "weather-new.example.invalid") throw java.io.IOException("review injected import weather failure")
                    real.setWeatherAuth(auth)
                }
                override suspend fun setAiConsent(consent: AiConsent) {
                    if (consent == AiConsent.Granted) {
                        atConsentWrite.complete(Unit)
                        continueConsent.await()
                    }
                    real.setAiConsent(consent)
                }
            }
            val credentials = BackupCredentials(importedAi.baseUrl, importedAi.apiKey, importedAi.model,
                weatherApiHost = "weather-new.example.invalid")
            val encrypted = BackupCredentialCipher.encrypt(
                json.encodeToString(BackupCredentials.serializer(), credentials), password)
            val manifest = BackupManifest(exportedAt = 1, wardrobes = listOf(wardrobe()),
                settings = BackupSettings(weatherCity = "备份城市", stylePreference = "备份风格",
                    encryptedCredentials = encrypted))
            val importing = async(Dispatchers.IO) { runCatching { repo(failing).import(zip(manifest), password) } }
            try {
                withTimeout(10_000) { atConsentWrite.await() }
                real.saveAiConfig(concurrentAi)
            } finally {
                continueConsent.complete(Unit)
            }
            assertTrue(withTimeout(10_000) { importing.await() }.isFailure)
            assertEquals(0, db.backupDao().wardrobes().size)
            val current = real.settings.first()
            assertEquals(concurrentAi, current.aiConfig)
            assertNotEquals("concurrent config must not inherit old Granted", AiConsent.Granted, current.aiConsent)
            assertEquals(AiStatus.Unverified, current.aiStatus)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
