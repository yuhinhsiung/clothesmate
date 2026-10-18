package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yuhinhsiung.clothesmate.core.database.entity.WearRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 穿着记录 DAO —— 开发文档 §2.4.2 T-05/T-06。
 *
 * 「今天穿这套」事务三处更新之一（另两处：穿搭 wearCount、每件衣物 wearCount）。
 * 同一天允许多条记录，回滚不做（§12.1 #13）。
 */
@Dao
interface WearRecordDao {

    @Insert
    suspend fun insert(record: WearRecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<WearRecordEntity>): List<Long>

    @Query("SELECT * FROM wear_record WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: String): Flow<List<WearRecordEntity>>

    @Query("SELECT * FROM wear_record WHERE date = :date ORDER BY createdAt DESC")
    suspend fun getByDate(date: String): List<WearRecordEntity>

    /** 月历查询：date 是 yyyy-MM-dd 字符串，字典序即时间序。 */
    @Query(
        "SELECT * FROM wear_record WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC, createdAt ASC",
    )
    fun observeBetween(startDate: String, endDate: String): Flow<List<WearRecordEntity>>

    @Query("UPDATE wear_record SET note = :note WHERE id = :recordId")
    suspend fun updateNote(recordId: Long, note: String?)

    @Query("SELECT * FROM wear_record ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<WearRecordEntity>>

    /** 日历标记：哪些日期有记录（T-06） */
    @Query("SELECT DISTINCT date FROM wear_record ORDER BY date DESC")
    fun observeDatesWithRecords(): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM wear_record WHERE date LIKE :yearMonth || '%'")
    fun observeDatesInMonth(yearMonth: String): Flow<List<String>>

    @Query("SELECT COUNT(DISTINCT date) FROM wear_record")
    fun observeDistinctDayCount(): Flow<Int>

    /** 当日已记录套数（今日页「今日已记录 n 套」） */
    @Query("SELECT COUNT(*) FROM wear_record WHERE date = :date")
    fun observeCountByDate(date: String): Flow<Int>
}
