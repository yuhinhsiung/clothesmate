package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.model.sortTagsByFrequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 标签词典测试 —— 开发计划 M1-21 验收项：
 * - 重复自定义不产生第二条（type + value 唯一，insertOrIncrement 走 upsert）
 * - 同值不同 type 是两条（style / scene 各自成键）
 * - 联想排序按使用频率降序、与预设同权（sortTagsByFrequency 纯函数）
 * - 预设幂等补种（IGNORE 冲突策略，isPreset 标记）
 */
@RunWith(AndroidJUnit4::class)
class TagDictionaryDaoTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var dao: TagDictionaryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.tagDictionaryDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun insertOrIncrementDoesNotDuplicateSameTypeValue() = runTest {
        // review B-34：「登记一次使用」语义——首插即计 1，二次使用递增为 2
        dao.insertOrIncrement("style", "极简", isPreset = false)
        dao.insertOrIncrement("style", "极简", isPreset = false)

        val rows = dao.getByType("style")
        assertEquals(1, rows.size)
        assertEquals(2, rows.first().usageCount)
    }

    @Test
    fun sameValueDifferentTypeAreDistinctRows() = runTest {
        dao.insertOrIncrement("style", "通勤", isPreset = false)
        dao.insertOrIncrement("scene", "通勤", isPreset = false)

        assertEquals(1, dao.getByType("style").size)
        assertEquals(1, dao.getByType("scene").size)
    }

    @Test
    fun presetSeedIsIdempotentAndDoesNotClobberUsageCount() = runTest {
        // 用户已用过该词：insertOrIncrement 首次即计 1（B-34 修正后语义）
        dao.insertOrIncrement("style", "复古", isPreset = false)
        assertEquals(1, dao.getByType("style").first().usageCount)
        // 启动补种预设：IGNORE 冲突策略下同 type+value 返回 -1（行已存在，不覆盖）
        val seedResult = dao.insert(TagDictionaryEntity(type = "style", value = "复古", isPreset = true))
        assertEquals(-1L, seedResult)

        val rows = dao.getByType("style")
        assertEquals(1, rows.size)
        assertEquals(1, rows.first().usageCount)
        // 首条写入的自定义标记保留（补种不动既有行）
        assertEquals(false, rows.first().isPreset)
    }

    @Test
    fun sortTagsByFrequencyPutsHighUsageFirstRegardlessOfPreset() {
        val rows = listOf(
            TagDictionaryEntity(id = 1, type = "style", value = "预设低频", isPreset = true, usageCount = 0),
            TagDictionaryEntity(id = 2, type = "style", value = "自定义高频", isPreset = false, usageCount = 5),
            TagDictionaryEntity(id = 3, type = "style", value = "自定义低频", isPreset = false, usageCount = 0),
            TagDictionaryEntity(id = 4, type = "style", value = "预设高频", isPreset = true, usageCount = 9),
        )
        // 与预设同权：只看频率，同频按字典序
        assertEquals(
            listOf("预设高频", "自定义高频", "自定义低频", "预设低频"),
            sortTagsByFrequency(rows),
        )
    }

    @Test
    fun seasonVocabularyIsFixedFourValues() {
        // 季节词表全站统一 春/夏/秋/冬（衣物与穿搭一致，开发文档 §3.4）
        assertEquals(
            listOf("spring", "summer", "autumn", "winter"),
            com.yuhinhsiung.clothesmate.core.model.Season.entries.map { it.key },
        )
        assertEquals(listOf("春", "夏", "秋", "冬"),
            com.yuhinhsiung.clothesmate.core.model.Season.entries.map { it.displayName },
        )
    }
}
