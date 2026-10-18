package com.yuhinhsiung.clothesmate.core.model

import java.time.LocalDate

/** 穿着记录（M3-06/M3-08）：一天可多条，outfitId 可空（历史补记/穿搭删除后）。 */
data class WearRecord(
    val id: Long,
    val date: LocalDate,
    val outfitId: Long?,
    val scheduleTags: List<String>,
    /** 当日天气快照 JSON；null = 当时天气不可用 */
    val weatherJson: String?,
    val note: String?,
    val createdAt: Long,
    val outfit: Outfit?,
    val items: List<WearRecordItem>,
) {
    val coverPath: String? get() = outfit?.coverPath
    val outfitName: String? get() = outfit?.displayName
    val itemCount: Int get() = items.size
}

/** 历史卡清单的一行：编号 + 名称 + 衣柜位置（M3-06「已取出 n/N」）。 */
data class WearRecordItem(
    val itemId: Long,
    val code: String,
    val displayName: String,
    val wardrobeName: String,
    val deleted: Boolean = false,
)

enum class WearFailure {
    OUTFIT_NOT_FOUND,
    EMPTY_OUTFIT,
    ITEM_UNAVAILABLE,
    ITEM_STORED,
    INVALID_DATE,
}

class WearException(
    val failure: WearFailure,
    message: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
