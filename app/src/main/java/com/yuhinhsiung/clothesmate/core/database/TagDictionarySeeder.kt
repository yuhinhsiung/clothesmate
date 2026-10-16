package com.yuhinhsiung.clothesmate.core.database

import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签词典预设种子（M1-21）。
 *
 * 开发文档 §2.2.3 / §2.4.2 / §3.4：预设风格、场景标签与用户自定义同权展示、
 * 按使用频率联想。预设**随应用启动幂等写入** tag_dictionary（isPreset = true，
 * type+value 唯一索引挡重复），不写入内存硬编码表——词典是唯一词源，避免「代码一份、
 * 库里一份」的两张皮。
 *
 * 季节词不入词典：季节是固定四值枚举（§3.4「全站统一词表」），表单直接用 [com.yuhinhsiung.clothesmate.core.model.Season]。
 */
@Singleton
class TagDictionarySeeder @Inject constructor(
    private val tagDictionaryDao: TagDictionaryDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** 启动时补种（IGNORE 冲突策略，幂等；用户自定义过同名词也不受影响） */
    fun seedOnStart() {
        scope.launch {
            STYLE_PRESETS.forEach { value ->
                tagDictionaryDao.insert(
                    TagDictionaryEntity(type = TYPE_STYLE, value = value, isPreset = true),
                )
            }
            SCENE_PRESETS.forEach { value ->
                tagDictionaryDao.insert(
                    TagDictionaryEntity(type = TYPE_SCENE, value = value, isPreset = true),
                )
            }
        }
    }

    companion object {
        const val TYPE_STYLE = "style"
        const val TYPE_SCENE = "scene"

        /** 风格预设（开发文档 §2.2.3 候选值） */
        val STYLE_PRESETS = listOf("休闲", "通勤", "运动", "甜美", "复古", "街头", "正式", "简约")

        /** 场景预设（开发文档 §2.4.2 T-02 行程词表同源） */
        val SCENE_PRESETS = listOf("通勤", "会议", "运动", "约会", "旅行", "居家")
    }
}
