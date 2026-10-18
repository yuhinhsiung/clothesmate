package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R02：创建模式没有有效暂存原图时**不能保存**（防止无图衣物）；
 * 编辑模式照片只读，不要求暂存原图。
 */
class ClothingFormCanSaveTest {

    private fun createState(
        imageReady: Boolean = true,
        isEdit: Boolean = false,
        compressing: Boolean = false,
    ) = ClothingFormUiState(
        isEdit = isEdit,
        imageReady = imageReady,
        compressing = compressing,
        wardrobeId = 1L,
        code = "42",
    )

    @Test
    fun createWithoutReadyImageCannotSave() {
        assertFalse(createState(imageReady = false).canSave)
    }

    @Test
    fun createWithReadyImageCanSave() {
        assertTrue(createState(imageReady = true).canSave)
    }

    @Test
    fun editCanSaveWithoutStagedOriginal() {
        assertTrue(createState(imageReady = false, isEdit = true).canSave)
    }

    @Test
    fun compressStateBlocksSaveEvenIfImageFlagIsStale() {
        assertFalse(createState(imageReady = true, compressing = true).canSave)
    }
}
