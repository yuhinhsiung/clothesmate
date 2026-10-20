package com.yuhinhsiung.clothesmate.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.dao.BackupDao
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.CodeHistoryDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitCategoryDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.dao.WearRecordDao
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingCodeHistoryEntity
import com.yuhinhsiung.clothesmate.core.datastore.AiConfig
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.weather.WeatherAuthConfig
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface BackupRepository {
    suspend fun export(uri: Uri): BackupSummary
    suspend fun inspect(uri: Uri): BackupSummary
    suspend fun import(uri: Uri): ImportResult
}

/**
 * 纯本地图片 + JSON 备份（M4-05）。
 *
 * 导入不合并同名数据：每个实体分配新 id 并重映射外键。衣物编号优先保留；
 * 与当前库或历史占用冲突时交给 [CodeAllocator] 顺延。图片按原相对路径从 zip
 * 取出，写到新 id 对应的私有目录；缺失图片不阻断导入，只把路径留空。
 */
@Singleton
class DefaultBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ClothesMateDatabase,
    private val backupDao: BackupDao,
    private val wardrobeDao: WardrobeDao,
    private val clothingDao: ClothingItemDao,
    private val codeHistoryDao: CodeHistoryDao,
    private val outfitDao: OutfitDao,
    private val categoryDao: OutfitCategoryDao,
    private val wearRecordDao: WearRecordDao,
    private val tagDao: TagDictionaryDao,
    private val imageStorage: ImageStorage,
    private val codeAllocator: CodeAllocator,
    private val userSettingsStore: UserSettingsStore,
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val settings = userSettingsStore.settings.first().let { current ->
            BackupSettings(
                aiBaseUrl = current.aiConfig.baseUrl,
                aiApiKey = current.aiConfig.apiKey,
                aiModel = current.aiConfig.model,
                weatherApiHost = current.weatherAuth.apiHost,
                weatherDeveloperId = current.weatherAuth.developerId,
                weatherProjectId = current.weatherAuth.projectId,
                weatherKeyId = current.weatherAuth.keyId,
                weatherPrivateKeyPem = current.weatherAuth.privateKeyPem,
                weatherCity = current.weatherCity,
                stylePreference = current.stylePreference,
            )
        }
        val manifest = db.withTransaction {
            BackupManifest(
                exportedAt = System.currentTimeMillis(),
                wardrobes = backupDao.wardrobes(),
                clothing = backupDao.clothing(),
                codeHistory = backupDao.codeHistory(),
                outfitCategories = backupDao.outfitCategories(),
                outfits = backupDao.outfits(),
                outfitItems = backupDao.outfitItems(),
                outfitCategoryRefs = backupDao.outfitCategoryRefs(),
                wearRecords = backupDao.wearRecords(),
                tags = backupDao.tags(),
                settings = settings,
            )
        }
        val images = linkedSetOf<String>()
        manifest.wardrobes.mapNotNullTo(images) { it.coverImagePath }
        manifest.clothing.forEach {
            it.originalImagePath?.let(images::add)
            it.cutoutImagePath?.let(images::add)
        }
        manifest.outfits.forEach { images.addAll(it.photoPaths) }

        val output = context.contentResolver.openOutputStream(uri)
            ?: throw BackupException("无法创建备份文件")
        output.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManifest.ENTRY_NAME))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                images.forEach { path ->
                    val file = imageStorage.toFile(path) ?: return@forEach
                    if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        manifest.toSummary()
    }

    override suspend fun inspect(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val temp = copyToTemp(uri)
        try {
            ZipFile(temp).use { zip -> zip.readManifest().toSummary() }
        } finally {
            temp.delete()
        }
    }

    override suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val temp = copyToTemp(uri)
        try {
            ZipFile(temp).use { zip ->
                val manifest = zip.readManifest()
                if (manifest.version != BackupManifest.CURRENT_VERSION) {
                    throw BackupException("备份版本 ${manifest.version} 不受支持")
                }
                db.withTransaction { importManifest(zip, manifest) }
            }
        } finally {
            temp.delete()
        }
    }

    private suspend fun importManifest(zip: ZipFile, manifest: BackupManifest): ImportResult {
        var images = 0
        var skipped = 0
        val wardrobeMap = mutableMapOf<Long, Long>()
        manifest.wardrobes.forEach { old ->
            val newId = wardrobeDao.insert(old.copy(id = 0, coverImagePath = null))
            wardrobeMap[old.id] = newId
            val cover = copyImage(zip, old.coverImagePath, imageStorage.wardrobeCoverFile(newId))
            if (cover != null) {
                images++
                wardrobeDao.update(old.copy(id = newId, coverImagePath = cover))
            }
        }

        val categoryMap = mutableMapOf<Long, Long>()
        manifest.outfitCategories.forEach { old ->
            val newId = categoryDao.insert(old.copy(id = 0, coverOutfitId = null))
            categoryMap[old.id] = newId
        }

        val clothingMap = mutableMapOf<Long, Long>()
        manifest.clothing.forEach { old ->
            val wardrobeId = wardrobeMap[old.wardrobeId]
            if (wardrobeId == null) {
                skipped++
                return@forEach
            }
            val code = availableCode(old.code)
            val newId = clothingDao.insert(
                old.copy(
                    id = 0,
                    code = code,
                    wardrobeId = wardrobeId,
                    originalImagePath = null,
                    cutoutImagePath = null,
                ),
            )
            clothingMap[old.id] = newId
            val original = copyImage(zip, old.originalImagePath, imageStorage.clothingOriginalFile(newId))
            val cutout = copyImage(zip, old.cutoutImagePath, imageStorage.clothingCutoutFile(newId))
            images += listOfNotNull(original, cutout).size
            clothingDao.update(
                old.copy(
                    id = newId,
                    code = code,
                    wardrobeId = wardrobeId,
                    originalImagePath = original,
                    cutoutImagePath = cutout,
                ),
            )
            codeHistoryDao.insertIfAbsent(
                ClothingCodeHistoryEntity(
                    code = code,
                    holderId = newId,
                    recordedAt = old.createdAt,
                ),
            )
        }

        val outfitMap = mutableMapOf<Long, Long>()
        manifest.outfits.forEach { old ->
            val newId = outfitDao.insert(old.copy(id = 0, photoPaths = emptyList()))
            outfitMap[old.id] = newId
            val photos = old.photoPaths.mapIndexedNotNull { index, path ->
                copyImage(zip, path, imageStorage.outfitPhotoFile(newId, index))?.also { images++ }
            }
            outfitDao.update(old.copy(id = newId, photoPaths = photos))
        }

        manifest.outfitItems.forEach { ref ->
            val outfitId = outfitMap[ref.outfitId] ?: return@forEach
            val itemId = clothingMap[ref.clothingItemId] ?: return@forEach
            outfitDao.insertCrossRef(OutfitItemCrossRef(outfitId, itemId, ref.position))
        }
        manifest.outfitCategoryRefs.forEach { ref ->
            val outfitId = outfitMap[ref.outfitId] ?: return@forEach
            val categoryId = categoryMap[ref.categoryId] ?: return@forEach
            categoryDao.insertCrossRef(OutfitCategoryCrossRef(outfitId, categoryId))
        }
        manifest.outfitCategories.forEach { old ->
            val newId = categoryMap[old.id] ?: return@forEach
            val cover = old.coverOutfitId?.let(outfitMap::get)
            if (cover != null) categoryDao.update(old.copy(id = newId, coverOutfitId = cover))
        }

        manifest.wearRecords.forEach { old ->
            wearRecordDao.insert(
                old.copy(id = 0, outfitId = old.outfitId?.let(outfitMap::get)),
            )
        }
        manifest.tags.forEach { tag -> tagDao.insert(tag.copy(id = 0)) }
        manifest.settings?.let { settings ->
            if (settings.aiBaseUrl.isNotBlank() || settings.aiApiKey.isNotBlank() || settings.aiModel.isNotBlank()) {
                userSettingsStore.saveAiConfig(
                    AiConfig(settings.aiBaseUrl, settings.aiApiKey, settings.aiModel),
                )
            }
            if (settings.weatherApiHost.isNotBlank() || settings.weatherPrivateKeyPem.isNotBlank()) {
                userSettingsStore.setWeatherAuth(
                    WeatherAuthConfig(
                        apiHost = settings.weatherApiHost,
                        developerId = settings.weatherDeveloperId,
                        projectId = settings.weatherProjectId,
                        keyId = settings.weatherKeyId,
                        privateKeyPem = settings.weatherPrivateKeyPem,
                    ),
                )
            }
            if (settings.weatherCity.isNotBlank()) userSettingsStore.setWeatherCity(settings.weatherCity)
            if (settings.stylePreference.isNotBlank()) {
                userSettingsStore.setStylePreference(settings.stylePreference)
            }
        }
        return ImportResult(
            wardrobes = wardrobeMap.size,
            clothing = clothingMap.size,
            outfits = outfitMap.size,
            wearRecords = manifest.wearRecords.size,
            images = images,
            skipped = skipped,
        )
    }

    /** 保留原编号；被当前衣物或历史占用时顺延。 */
    private suspend fun availableCode(preferred: String): String {
        val code = preferred.trim()
        if (code.isNotEmpty() && backupDao.clothingByCode(code) == null &&
            codeHistoryDao.countByCode(code) == 0
        ) {
            return code
        }
        return codeAllocator.nextCode()
    }

    private suspend fun copyImage(zip: ZipFile, source: String?, target: File): String? {
        if (source.isNullOrBlank()) return null
        val entry = zip.getEntry(source) ?: return null
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) return null
        return runCatching {
            zip.getInputStream(entry).use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            imageStorage.toRelative(target)
        }.getOrNull()
    }

    private fun copyToTemp(uri: Uri): File {
        val temp = File.createTempFile("backup-", ".zip", context.cacheDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("无法读取备份文件")
        input.use { source -> temp.outputStream().use { source.copyTo(it) } }
        return temp
    }

    private fun ZipFile.readManifest(): BackupManifest {
        val entry = getEntry(BackupManifest.ENTRY_NAME)
            ?: throw BackupException("不是 ClothesMate 备份：缺少 manifest.json")
        val bytes = getInputStream(entry).use { it.readBytes() }
        return runCatching {
            json.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())
        }.getOrElse { throw BackupException("备份清单损坏：${it.message}") }
    }

    private fun BackupManifest.toSummary() = BackupSummary(
        version = version,
        exportedAt = exportedAt,
        wardrobes = wardrobes.size,
        clothing = clothing.size,
        outfits = outfits.size,
        wearRecords = wearRecords.size,
        tags = tags.size,
    )
}
