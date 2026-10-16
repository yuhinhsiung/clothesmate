package com.yuhinhsiung.clothesmate.core.model

import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity

/**
 * 标签联想排序 —— M1-21「联想建议按使用频率降序，与预设同权展示」。
 *
 * 纯函数（JVM 可直测）：使用次数降序，同频按字典序稳定排序；预设与自定义
 * **不区分先后**（同权），仅由使用频率决定位置。
 */
fun sortTagsByFrequency(rows: List<TagDictionaryEntity>): List<String> =
    rows.sortedWith(
        compareByDescending<TagDictionaryEntity> { it.usageCount }.thenBy { it.value },
    ).map { it.value }
