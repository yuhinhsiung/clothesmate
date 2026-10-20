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
    /** 可选：AI/天气/风格配置；含敏感 Key，zip 需按敏感文件保管。 */
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val ENTRY_NAME = "manifest.json"
    }
}

@Serializable
data class BackupSettings(
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val weatherApiHost: String = "",
    val weatherDeveloperId: String = "",
    val weatherProjectId: String = "",
    val weatherKeyId: String = "",
    val weatherPrivateKeyPem: String = "",
    val weatherCity: String = "",
    val stylePreference: String = "",
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
