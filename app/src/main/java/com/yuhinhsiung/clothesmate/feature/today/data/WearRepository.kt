package com.yuhinhsiung.clothesmate.feature.today.data

import androidx.room.withTransaction
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.dao.WearRecordDao
import com.yuhinhsiung.clothesmate.core.database.entity.WearRecordEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.model.WearException
import com.yuhinhsiung.clothesmate.core.model.WearFailure
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.core.model.WearRecordItem
import com.yuhinhsiung.clothesmate.core.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface WearRepository {

    /** 今日/某日记录，按创建时间升序（同日多套可横向翻页）。 */
    fun observeRecords(date: LocalDate): Flow<List<WearRecord>>

    /** 月历数据源，date 区间闭区间。 */
    fun observeRecordsInMonth(month: YearMonth): Flow<List<WearRecord>>

    /**
     * 确认穿着（M3-06）：wear_record + 穿搭计数 + 每件衣物计数，同一事务。
     *
     * @param date 今天或补记的历史日期；`lastWornAt` 只前进不回拨
     * @param weatherJson 当日天气快照；null = 天气不可用
     * @param note 可选备注（M3-08 快照）
     */
    suspend fun confirmWear(
        outfitId: Long,
        date: LocalDate,
        scheduleTags: List<String>,
        weatherJson: String?,
        note: String?,
    ): WearRecord

    suspend fun updateNote(recordId: Long, note: String?)
}

@Singleton
class DefaultWearRepository @Inject constructor(
    private val db: ClothesMateDatabase,
    private val wearDao: WearRecordDao,
    private val outfitDao: OutfitDao,
    private val clothingDao: ClothingItemDao,
    private val wardrobeDao: WardrobeDao,
) : WearRepository {

    override fun observeRecords(date: LocalDate): Flow<List<WearRecord>> =
        mapRecords(wearDao.observeByDate(date.toString()))

    override fun observeRecordsInMonth(month: YearMonth): Flow<List<WearRecord>> =
        mapRecords(
            wearDao.observeBetween(
                startDate = month.atDay(1).toString(),
                endDate = month.atEndOfMonth().toString(),
            ),
        )

    override suspend fun confirmWear(
        outfitId: Long,
        date: LocalDate,
        scheduleTags: List<String>,
        weatherJson: String?,
        note: String?,
    ): WearRecord {
        if (date.isAfter(LocalDate.now())) {
            throw WearException(WearFailure.INVALID_DATE)
        }
        val wornAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val createdAt = System.currentTimeMillis()
        // review R06：存在性/收起校验必须和写入在同一事务里。否则校验通过后、
        // 写入前若衣物被收起，仍会把一件已收起的衣物记成“今天穿过”。
        val recordId = db.withTransaction {
            outfitDao.findById(outfitId)
                ?: throw WearException(WearFailure.OUTFIT_NOT_FOUND)
            val orderedIds = outfitDao.getClothingIds(outfitId)
            if (orderedIds.isEmpty()) throw WearException(WearFailure.EMPTY_OUTFIT)
            val byId = clothingDao.findByIds(orderedIds).associateBy { it.id }
            val orderedItems = orderedIds.map { id ->
                byId[id] ?: throw WearException(WearFailure.ITEM_UNAVAILABLE)
            }
            if (orderedItems.any { it.status == ClothingStatus.STORED }) {
                throw WearException(WearFailure.ITEM_STORED)
            }
            val id = wearDao.insert(
                WearRecordEntity(
                    date = date.toString(),
                    outfitId = outfitId,
                    scheduleTags = scheduleTags,
                    weatherJson = weatherJson,
                    note = note?.trim()?.takeIf(String::isNotEmpty),
                    createdAt = createdAt,
                ),
            )
            outfitDao.recordWear(outfitId, wornAt)
            orderedItems.forEach { item -> clothingDao.recordWear(item.id, wornAt) }
            // review R06：提交前再核对一次「将被计数的集合」；并发/拦截器若在首次读取后
            // 把衣物收起，这里必须抛错让整个事务回滚，而不是留下一条已穿记录。
            val finalItems = clothingDao.findByIds(orderedIds)
            if (finalItems.size != orderedIds.size ||
                finalItems.any { it.status == ClothingStatus.STORED }
            ) {
                throw WearException(WearFailure.ITEM_STORED)
            }
            id
        }

        return observeRecords(date).first().first { it.id == recordId }
    }

    override suspend fun updateNote(recordId: Long, note: String?) {
        wearDao.updateNote(recordId, note?.trim()?.takeIf(String::isNotEmpty))
    }

    private fun mapRecords(records: Flow<List<WearRecordEntity>>): Flow<List<WearRecord>> =
        combine(
            records,
            outfitDao.observeAll(),
            outfitDao.observeItemRefs(),
            clothingDao.observeAllIncludingDeleted(),
            wardrobeDao.observeAll(),
        ) { rows, outfits, refs, clothing, wardrobes ->
            val outfitById = outfits.associate { entity ->
                entity.id to entity.toDomain(itemCount = refs.count { it.outfitId == entity.id })
            }
            val clothingById = clothing.associateBy { it.id }
            val refsByOutfit = refs.groupBy { it.outfitId }
            val wardrobeNames = wardrobes.associate { it.id to it.name }

            rows.map { row ->
                val outfit = row.outfitId?.let { outfitById[it] }
                val items = row.outfitId
                    ?.let { refsByOutfit[it] }
                    .orEmpty()
                    .sortedBy { it.position }
                    .mapNotNull { ref ->
                        clothingById[ref.clothingItemId]?.let { item ->
                            val domain = item.toDomain()
                            WearRecordItem(
                                itemId = domain.id,
                                code = domain.code,
                                displayName = domain.displayName,
                                wardrobeName = wardrobeNames[domain.wardrobeId] ?: "未归柜",
                                deleted = item.deletedAt != null,
                            )
                        }
                    }
                WearRecord(
                    id = row.id,
                    date = runCatching { LocalDate.parse(row.date) }
                        .getOrDefault(LocalDate.ofEpochDay(0)),
                    outfitId = row.outfitId,
                    scheduleTags = row.scheduleTags,
                    weatherJson = row.weatherJson,
                    note = row.note,
                    createdAt = row.createdAt,
                    outfit = outfit,
                    items = items,
                )
            }.sortedBy { it.createdAt }
        }
}
