package com.yuhinhsiung.clothesmate.core.backup

import com.yuhinhsiung.clothesmate.core.database.entity.ClothingCodeHistoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WearRecordEntity
import kotlinx.serialization.Serializable

/** zip 内的清单文件；图片以原相对路径独立存放，导入时按新 id 重命名。 */
@Serializable
data class BackupManifest(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val wardrobes: List<WardrobeEntity> = emptyList(),
    val clothing: List<ClothingItemEntity> = emptyList(),
    val codeHistory: List<ClothingCodeHistoryEntity> = emptyList(),
    val outfitCategories: List<OutfitCategoryEntity> = emptyList(),
    val outfits: List<OutfitEntity> = emptyList(),
    val outfitItems: List<OutfitItemCrossRef> = emptyList(),
    val outfitCategoryRefs: List<OutfitCategoryCrossRef> = emptyList(),
    val wearRecords: List<WearRecordEntity> = emptyList(),
    val tags: List<TagDictionaryEntity> = emptyList(),
    /** 可选：非敏感偏好 + 用户口令加密的 AI/天气凭据。 */
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val ENTRY_NAME = "manifest.json"
    }
}

@Serializable
data class BackupSettings(
    /** 非敏感偏好；凭据只以 encryptedCredentials 形式出现。 */
    val weatherCity: String = "",
    val stylePreference: String = "",
    /** 用户口令派生的 AES-GCM 密文（Base64），未设置凭据时为 null。 */
    val encryptedCredentials: String? = null,
)

/** 加密前的凭据载荷，绝不直接写进 manifest。 */
@Serializable
data class BackupCredentials(
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val weatherApiHost: String = "",
    val weatherDeveloperId: String = "",
    val weatherProjectId: String = "",
    val weatherKeyId: String = "",
    val weatherPrivateKeyPem: String = "",
)

data class BackupSummary(
    val version: Int,
    val exportedAt: Long,
    val wardrobes: Int,
    val clothing: Int,
    val outfits: Int,
    val wearRecords: Int,
    val tags: Int,
) {
    val total: Int get() = wardrobes + clothing + outfits + wearRecords + tags
}

data class ImportResult(
    val wardrobes: Int,
    val clothing: Int,
    val outfits: Int,
    val wearRecords: Int,
    val images: Int,
    val skipped: Int,
)

class BackupException(message: String) : Exception(message)
