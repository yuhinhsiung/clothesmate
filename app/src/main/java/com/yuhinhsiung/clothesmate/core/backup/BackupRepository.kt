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
import com.yuhinhsiung.clothesmate.core.datastore.AiConsent
import com.yuhinhsiung.clothesmate.core.datastore.AiConsentExpectation
import com.yuhinhsiung.clothesmate.core.datastore.AiStatus
import com.yuhinhsiung.clothesmate.core.datastore.UserSettings
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface BackupRepository {
    /** [password] 用于加密 AI/天气凭据；不落盘。 */
    suspend fun export(uri: Uri, password: String): BackupSummary
    suspend fun inspect(uri: Uri): BackupSummary
    /** [password] 必须与导出时一致；备份无凭据时可忽略。 */
    suspend fun import(uri: Uri, password: String): ImportResult
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
    @Suppress("UNUSED_PARAMETER") codeAllocator: CodeAllocator,
    private val userSettingsStore: UserSettingsStore,
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        /** 已物理删除的旧持有人：保留占用，但绝不指向任何新实体。 */
        const val NO_HOLDER = -1L
    }

    override suspend fun export(uri: Uri, password: String): BackupSummary = withContext(Dispatchers.IO) {
        BackupCredentialCipher.validatePassword(password)
        val settings = userSettingsStore.settings.first().let { current ->
            val credentials = BackupCredentials(
                aiBaseUrl = current.aiConfig.baseUrl,
                aiApiKey = current.aiConfig.apiKey,
                aiModel = current.aiConfig.model,
                weatherApiHost = current.weatherAuth.apiHost,
                weatherDeveloperId = current.weatherAuth.developerId,
                weatherProjectId = current.weatherAuth.projectId,
                weatherKeyId = current.weatherAuth.keyId,
                weatherPrivateKeyPem = current.weatherAuth.privateKeyPem,
            )
            val encrypted = BackupCredentialCipher.encrypt(
                json.encodeToString(BackupCredentials.serializer(), credentials),
                password,
            )
            BackupSettings(
                weatherCity = current.weatherCity,
                stylePreference = current.stylePreference,
                encryptedCredentials = encrypted,
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

    override suspend fun import(uri: Uri, password: String): ImportResult = withContext(Dispatchers.IO) {
        val temp = copyToTemp(uri)
        try {
            ZipFile(temp).use { zip ->
                val manifest = zip.readManifest()
                if (manifest.version !in 1..BackupManifest.CURRENT_VERSION) {
                    throw BackupException("备份版本 ${manifest.version} 不受支持")
                }
                // 在写数据库前解密；密码错误/凭据损坏时不产生半截导入。
                val credentials = manifest.settings?.encryptedCredentials?.let { encrypted ->
                    if (password.isBlank()) throw BackupException("这份备份的凭据已加密，请输入备份密码")
                    val plain = BackupCredentialCipher.decrypt(encrypted, password)
                    runCatching {
                        json.decodeFromString(BackupCredentials.serializer(), plain)
                    }.getOrElse { throw BackupException("备份凭据格式损坏") }
                }
                // 先写设置、再写实体：设置写失败时不会留下已提交的业务数据。
                // 实体事务失败时用 NonCancellable 补偿恢复旧设置，避免“配置改了、数据没进来”。
                val previous = userSettingsStore.settings.first()
                try {
                    applySettings(manifest.settings, credentials)
                    ImportSyncGate.pause(context, "AFTER_SETTINGS")
                    val result = db.withTransaction { importManifest(zip, manifest) }
                    ImportSyncGate.pause(context, "AFTER_COMMIT")
                    result
                } catch (failure: Throwable) {
                    // R04：补偿必须逐项进行。凭据写入失败时仍要恢复城市/风格/授权等
                    // 已写入的字段，不能因为第一个恢复动作再次失败而跳过其余字段。
                    val unrestored = withContext(NonCancellable) { restoreSettings(previous) }
                    if (failure is CancellationException) throw failure
                    if (unrestored.isEmpty()) throw failure
                    throw BackupException(
                        "导入失败：${failure.message ?: failure.javaClass.simpleName}；" +
                            "以下设置未能恢复：${unrestored.joinToString("、")}",
                        failure,
                    )
                }
            }
        } finally {
            temp.delete()
        }
    }

    private suspend fun applySettings(
        settings: BackupSettings?,
        credentials: BackupCredentials?,
    ) {
        if (settings != null) {
            if (settings.weatherCity.isNotBlank()) userSettingsStore.setWeatherCity(settings.weatherCity)
            if (settings.stylePreference.isNotBlank()) {
                userSettingsStore.setStylePreference(settings.stylePreference)
            }
        }
        credentials?.let { applyCredentials(it) }
    }

    /**
     * 尽力恢复导入前的设置，返回未能恢复的字段名。
     *
     * 每个字段独立恢复：凭据写入失败（例如 Keystore 不可用）不能让城市/风格
     * 停留在备份里的值。调用方在 NonCancellable 中执行本方法。
     */
    private suspend fun restoreSettings(previous: UserSettings): List<String> {
        val failed = mutableListOf<String>()
        suspend fun attempt(label: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failed += label
            }
        }
        attempt("AI 配置") { userSettingsStore.saveAiConfig(previous.aiConfig) }
        // 授权/验证状态必须绑定到“实际保留下来的配置”。若旧配置没能恢复，
        // 新配置仍是导入时写入的 B，不能把 A 的 Granted/Verified 移交给 B。
        val actualAiConfig = runCatching { userSettingsStore.settings.first().aiConfig }.getOrNull()
        if (actualAiConfig == previous.aiConfig) {
            withContext(AiConsentExpectation(previous.aiConfig)) {
                attempt("AI 授权") { userSettingsStore.setAiConsent(previous.aiConsent) }
                attempt("AI 状态") {
                    userSettingsStore.setAiStatus(
                        previous.aiStatus,
                        previous.aiStatusDetail,
                        expectedConfig = previous.aiConfig,
                    )
                }
            }
        } else {
            // 配置未恢复：撤销授权，避免后台把照片发往新端点；验证状态重置为未验证。
            attempt("AI 授权") { userSettingsStore.setAiConsent(AiConsent.Unasked) }
            attempt("AI 状态") {
                userSettingsStore.setAiStatus(
                    AiStatus.Unverified,
                    "导入未完成，请重新配置并测试连接",
                    expectedConfig = null,
                )
            }
            if ("AI 配置" !in failed) failed += "AI 配置"
        }
        // 授权写与状态写之间仍可能插入普通配置保存（saveAiConfig 会重置授权），
        // 因此补偿收尾时再做一次基于实际配置的收敛；这一步只允许把状态收紧。
        val actualAfter = runCatching { userSettingsStore.settings.first().aiConfig }.getOrNull()
        if (actualAfter != previous.aiConfig) {
            attempt("AI 授权") { userSettingsStore.setAiConsent(AiConsent.Unasked) }
            attempt("AI 状态") {
                userSettingsStore.setAiStatus(
                    AiStatus.Unverified,
                    "导入未完成，请重新配置并测试连接",
                    expectedConfig = null,
                )
            }
            if ("AI 配置" !in failed) failed += "AI 配置"
        }
        attempt("天气认证") { userSettingsStore.setWeatherAuth(previous.weatherAuth) }
        attempt("城市") { userSettingsStore.setWeatherCity(previous.weatherCity) }
        attempt("风格偏好") { userSettingsStore.setStylePreference(previous.stylePreference) }
        return failed
    }

    private suspend fun applyCredentials(credentials: BackupCredentials) {
        if (credentials.aiBaseUrl.isNotBlank() || credentials.aiApiKey.isNotBlank() || credentials.aiModel.isNotBlank()) {
            userSettingsStore.saveAiConfig(
                AiConfig(credentials.aiBaseUrl, credentials.aiApiKey, credentials.aiModel),
            )
        }
        if (credentials.weatherApiHost.isNotBlank() || credentials.weatherPrivateKeyPem.isNotBlank()) {
            userSettingsStore.setWeatherAuth(
                WeatherAuthConfig(
                    apiHost = credentials.weatherApiHost,
                    developerId = credentials.weatherDeveloperId,
                    projectId = credentials.weatherProjectId,
                    keyId = credentials.weatherKeyId,
                    privateKeyPem = credentials.weatherPrivateKeyPem,
                ),
            )
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
        val assignedCodes = mutableMapOf<Long, String>()
        val reservedCodes = mutableSetOf<String>()
        val oldClothingById = manifest.clothing.associateBy { it.id }
        // R02：备份内的当前编号与全部历史编号都属于本次导入的保留集合。
        // 不能只依赖本机 CodeAllocator——它看不到备份里已退役/已删除的号，
        // 冲突顺延时会把备份历史中的号再次发出去。
        val backupCodesByItem = manifest.clothing.associate { it.id to it.code.trim() }
        val backupHistoryHolder = manifest.codeHistory.associate { it.code.trim() to it.holderId }
        val occupiedCodes = buildSet {
            addAll(backupDao.clothingCodes())
            addAll(codeHistoryDao.codes())
        }
        manifest.clothing.forEach { old ->
            val wardrobeId = wardrobeMap[old.wardrobeId]
            if (wardrobeId == null) {
                skipped++
                return@forEach
            }
            val code = ImportCodeAllocator.allocate(
                preferred = old.code,
                oldItemId = old.id,
                backupCodesByItem = backupCodesByItem,
                backupHistoryHolder = backupHistoryHolder,
                occupiedCodes = occupiedCodes,
                assignedCodes = reservedCodes,
            )
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
            assignedCodes[old.id] = code
            reservedCodes += code
            val original = copyImage(zip, old.originalImagePath, imageStorage.clothingOriginalFile(newId))
            val cutout = copyImage(zip, old.cutoutImagePath, imageStorage.clothingCutoutFile(newId))
            images += listOfNotNull(original, cutout).size
            if (images >= 10) ImportSyncGate.pause(context, "DURING_IMAGES")
            clothingDao.update(
                old.copy(
                    id = newId,
                    code = code,
                    wardrobeId = wardrobeId,
                    originalImagePath = original,
                    cutoutImagePath = cutout,
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
        ImportSyncGate.pause(context, "BEFORE_COMMIT")
        manifest.tags.forEach { tag -> tagDao.insert(tag.copy(id = 0)) }
        // R02：完整恢复编号历史。仍存在的持有人重映射；已物理删除的旧持有人用 -1 占位，
        // 既保留“这个号曾被使用”的占用语义，又不会误撞新实体 id。
        manifest.codeHistory.forEach { history ->
            val oldHolder = history.holderId
            val newHolder = clothingMap[oldHolder] ?: NO_HOLDER
            val oldCurrentCode = oldClothingById[oldHolder]?.code
            val assigned = assignedCodes[oldHolder]
            if (assigned != null && history.code == oldCurrentCode && assigned != history.code) {
                // 冲突导致当前衣物改号：旧号作为退役历史保留，新号单独登记。
                codeHistoryDao.insertIfAbsent(history.copy(holderId = newHolder))
                codeHistoryDao.insertIfAbsent(
                    ClothingCodeHistoryEntity(
                        code = assigned,
                        holderId = newHolder,
                        recordedAt = history.recordedAt,
                    ),
                )
            } else {
                codeHistoryDao.insertIfAbsent(
                    history.copy(holderId = newHolder),
                )
            }
        }
        manifest.clothing.forEach { old ->
            val newId = clothingMap[old.id] ?: return@forEach
            val assigned = assignedCodes[old.id] ?: return@forEach
            val currentHistory = manifest.codeHistory.firstOrNull {
                it.code == old.code && it.holderId == old.id
            }
            codeHistoryDao.insertIfAbsent(
                ClothingCodeHistoryEntity(
                    code = assigned,
                    holderId = newId,
                    recordedAt = currentHistory?.recordedAt ?: old.createdAt,
                ),
            )
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

    /**
     * R02：导入专用的编号分配。
     *
     * 可用性必须同时覆盖四处，缺一不可：
     * 1. 本机现存/软删除衣物占用的编号；
     * 2. 本机编号历史（含已退役、持有人已物理删除的号）；
     * 3. 备份内其他衣物当前的编号；
     * 4. 备份内全部历史编号（持有人不是当前旧衣物时不可复用）。
     *
     * 旧实现只检查前三者的一部分，并直接调用只认本机库的 CodeAllocator，
     * 于是“本机冲突 → 顺延”会重新发出备份历史里的退役号。这里用导入批次
     * 内共享的 [assignedCodes] 做闭包分配，保证一次导入内也不重复。
     */
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
