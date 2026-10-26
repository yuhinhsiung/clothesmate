package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 标签词典 DAO —— 开发文档 §3.4 / M1-21。
 *
 * 自定义标签写入前 trim，type + value 唯一；联想建议按使用频率降序，与预设同权展示。
 */
@Dao
abstract class TagDictionaryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(tag: TagDictionaryEntity): Long

    @Update
    abstract suspend fun update(tag: TagDictionaryEntity)

    /**
     * 写入或递增使用计数：已存在则 usageCount +1，不存在则插入。
     * 用 IGNORE + 手动判断实现 upsert 语义，避免依赖 Room 2.7 才稳定的 @Upsert。
     *
     * review B-34：首插计数从 0 改 1——「登记一次使用」语义下首次使用就该计 1，
     * 否则自定义标签同频排序系统性落后预设一次，「与预设同权」有偏差
     * （预设由 Seeder 直接 insert、不经过本方法，恒从 0 起不受影响）。
     */
    @Query(
        "UPDATE tag_dictionary SET usageCount = usageCount + 1 WHERE type = :type AND value = :value",
    )
    abstract suspend fun incrementUsage(type: String, value: String)

    /**
     * Android 8/9 的 SQLite < 3.24 不支持 `ON CONFLICT ... DO UPDATE`（upsert），
     * 因此改为事务内 IGNORE + UPDATE：首插计数 1，冲突则递增。语义与 B-34 一致。
     */
    @Transaction
    open suspend fun insertOrIncrement(type: String, value: String, isPreset: Boolean) {
        val inserted = insert(
            TagDictionaryEntity(type = type, value = value, isPreset = isPreset, usageCount = 1),
        )
        if (inserted == -1L) incrementUsage(type, value)
    }

    @Query("SELECT * FROM tag_dictionary WHERE type = :type ORDER BY isPreset DESC, usageCount DESC, value ASC")
    abstract fun observeByType(type: String): Flow<List<TagDictionaryEntity>>

    @Query("SELECT * FROM tag_dictionary WHERE type = :type ORDER BY isPreset DESC, usageCount DESC, value ASC")
    abstract suspend fun getByType(type: String): List<TagDictionaryEntity>

    @Query("SELECT COUNT(*) FROM tag_dictionary WHERE type = :type AND value = :value")
    abstract suspend fun countOf(type: String, value: String): Int

    @Query("SELECT * FROM tag_dictionary WHERE type = :type AND value = :value")
    abstract suspend fun find(type: String, value: String): TagDictionaryEntity?
}
