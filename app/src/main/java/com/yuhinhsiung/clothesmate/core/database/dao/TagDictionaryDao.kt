package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 标签词典 DAO —— 开发文档 §3.4 / M1-21。
 *
 * 自定义标签写入前 trim，type + value 唯一；联想建议按使用频率降序，与预设同权展示。
 */
@Dao
interface TagDictionaryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagDictionaryEntity): Long

    @Update
    suspend fun update(tag: TagDictionaryEntity)

    /**
     * 写入或递增使用计数：已存在则 usageCount +1，不存在则插入。
     * 用 IGNORE + 手动判断实现 upsert 语义，避免依赖 Room 2.7 才稳定的 @Upsert。
     */
    @Query(
        """
        INSERT INTO tag_dictionary (type, value, isPreset, usageCount)
        VALUES (:type, :value, :isPreset, 0)
        ON CONFLICT(type, value) DO UPDATE SET usageCount = usageCount + 1
        """,
    )
    suspend fun insertOrIncrement(type: String, value: String, isPreset: Boolean)

    @Query("SELECT * FROM tag_dictionary WHERE type = :type ORDER BY isPreset DESC, usageCount DESC, value ASC")
    fun observeByType(type: String): Flow<List<TagDictionaryEntity>>

    @Query("SELECT * FROM tag_dictionary WHERE type = :type ORDER BY isPreset DESC, usageCount DESC, value ASC")
    suspend fun getByType(type: String): List<TagDictionaryEntity>

    @Query("SELECT COUNT(*) FROM tag_dictionary WHERE type = :type AND value = :value")
    suspend fun countOf(type: String, value: String): Int

    @Query("SELECT * FROM tag_dictionary WHERE type = :type AND value = :value")
    suspend fun find(type: String, value: String): TagDictionaryEntity?
}
