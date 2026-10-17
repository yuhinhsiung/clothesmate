package com.yuhinhsiung.clothesmate.feature.outfit.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 穿搭表单「是否有未保存改动」判定测试 —— review R-05 修复的直测载体。
 *
 * 旧缺陷：脏判定靠一个只有走字段 setter 才置位的 `userModified` 布尔，
 * **相册/拍照追加照片、移除照片、现场新建并选中分类都绕过它**。于是「只改了照片」
 * 的返回被直接丢弃、没有任何确认，暂存图也不清理。
 * 现在改成拿当前状态与初始快照逐项比对，本测试把每一条漏判路径都钉住。
 */
class OutfitFormDirtyStateTest {

    /** 编辑模式的起点：预填完成后固定 baseline，预填本身不算改动 */
    private fun editingState(
        existingPhotos: List<String> = listOf("outfit/1/0.jpg"),
        itemIds: List<Long> = listOf(1L, 2L),
    ): OutfitFormUiState {
        val loaded = OutfitFormUiState(
            isEdit = true,
            existingPaths = existingPhotos,
            selectedItemIds = itemIds,
        )
        return loaded.copy(baseline = loaded.snapshot())
    }

    @Test
    fun freshDraftIsNotModified() {
        assertFalse(editingState().userModified)
        assertFalse(OutfitFormUiState().userModified)
    }

    /** 关闭条件：只追加照片也要弹确认 */
    @Test
    fun appendingPhotoCountsAsModified() {
        val modified = editingState().copy(stagedDisplayPaths = listOf("/cache/tmp_images/staged_1.jpg"))
        assertTrue(modified.userModified)
    }

    /** 关闭条件：只删除照片也要弹确认 */
    @Test
    fun removingExistingPhotoCountsAsModified() {
        val modified = editingState().copy(
            existingPaths = emptyList(),
            removedExistingPaths = listOf("outfit/1/0.jpg"),
        )
        assertTrue(modified.userModified)
    }

    /** 关闭条件：现场新建并选中分类也要弹确认 */
    @Test
    fun creatingAndSelectingCategoryCountsAsModified() {
        val modified = editingState().copy(selectedCategoryIds = setOf(7L))
        assertTrue(modified.userModified)
    }

    @Test
    fun itemTagSeasonAndNoteChangesCountAsModified() {
        assertTrue(editingState().copy(selectedItemIds = listOf(2L, 1L)).userModified)
        assertTrue(editingState().copy(styleTags = listOf("简约")).userModified)
        assertTrue(editingState().copy(sceneTags = listOf("通勤")).userModified)
        assertTrue(editingState().copy(seasonTags = listOf("spring")).userModified)
        assertTrue(editingState().copy(note = "备注").userModified)
    }

    /** 加了又删光 = 什么都没改（不打扰用户），但已删的既有照片仍算改动 */
    @Test
    fun addingThenRemovingAllStagedPhotosGoesBackToClean() {
        val added = editingState().copy(stagedDisplayPaths = listOf("/cache/a.jpg"))
        assertTrue(added.userModified)
        assertFalse(added.copy(stagedDisplayPaths = emptyList()).userModified)
    }

    /** 新建模式：入口面板带来的首批照片也算「有内容」，返回时应确认 */
    @Test
    fun createModeEntryPhotosCountAsModified() {
        val created = OutfitFormUiState().copy(stagedDisplayPaths = listOf("/cache/tmp_images/staged_0.jpg"))
        assertTrue(created.userModified)
    }
}