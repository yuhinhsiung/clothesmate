package com.yuhinhsiung.clothesmate.core.image

import java.io.File

/**
 * 图片存储 —— 开发文档 §3.3「图片存储规范」/ §10、开发计划 M1-03。
 *
 * 目录结构（§3.3 原文）：
 *
 * ```text
 * /files/images/
 *    clothing/original/{id}.jpg      原图
 *    clothing/cutout/{id}.png        抠图
 *    outfit/{id}/{index}.jpg         穿搭照片（支持多张，index 从 0 起，0 = 封面）
 *    wardrobe_cover/{id}.jpg         衣柜封面
 * ```
 *
 * 两条口径（CLAUDE.md 红线 1）：
 * 1. 图片**只存应用私有目录**（`context.filesDir/images/`，无需任何存储权限）；
 * 2. **DB 只存相对路径**——调用方写库用 [toRelative] 的输出，读盘用 [toFile] 反转，
 *    两者互为逆运算。绝对路径（尤其 `/storage/emulated/...`）绝不出现在数据库里。
 *
 * ## M1-03 范围说明
 *
 * 本接口只负责**已分配实体的图片**（衣物的 {id}、穿搭的 {id}、衣柜的 {id}）。
 * 「拍照/选图后、id 尚未分配时照片先落哪」属录入流程（M1-14）的职责，
 * 其暂存目录与归位 API 随 M1-14 一并设计，不在本模块内。
 *
 * ## 删除口径（跨任务，易踩）
 *
 * | 场景 | 是否删文件 | 出处 |
 * |------|-----------|------|
 * | 衣物**软删除**（W-13 前半） | **不删**（Snackbar 撤销要复活，图片必须还在） | M1-13 验收 |
 * | 衣物**彻底删除**（W-13 后半） | 删原图 + 抠图 | W-13 / §12.1 #12 |
 * | 删除穿搭（M1-20） | 删该穿搭全部照片；连带物理清除的软删衣物另走 [deleteClothingImages] | M1-20 |
 * | 删除衣柜（W-02/W-12） | 只删封面；其中衣物按「转移/一并删除」弹层处理 | §4.4 第 4 条 |
 * | 「清理原图只留抠图」（M4-06） | 仅删已有抠图衣物的原图 | §10 / M4-06 |
 *
 * 软删而不删文件意味着**已删除衣物的图片仍占盘**——这是有意的取舍（换取可撤销），
 * 彻底清理的唯一入口是 W-13 后半段与 M4-06。
 */
interface ImageStorage {

    /** 衣物原图：`clothing/original/{id}.jpg` */
    fun clothingOriginalFile(id: Long): File

    /**
     * 衣物抠图：`clothing/cutout/{id}.png`。
     *
     * **注意格式是 PNG 不是 JPEG**（§3.3）：抠图产出透明背景，JPEG 不支持 alpha。
     * M1 阶段没有抠图，该文件不存在属正常；由 M2-01（TFLite）写入。
     */
    fun clothingCutoutFile(id: Long): File

    /**
     * 穿搭照片：`outfit/{id}/{index}.jpg`，[index] 从 0 起且 **0 = 封面**。
     *
     * 上限 9 张（`OutfitEntity.photoPaths`，§3.2）。本方法只保证按给定 id/index
     * 定位到正确文件，**不负责**「删掉中间一张后后面的前移补位」——重排属 M1-18
     * （穿搭照片编辑）职责，届时会有 index 空洞需要整理。
     */
    fun outfitPhotoFile(outfitId: Long, index: Int): File

    /** 衣柜封面：`wardrobe_cover/{id}.jpg` */
    fun wardrobeCoverFile(wardrobeId: Long): File

    /**
     * 绝对路径 → **相对路径**（写 DB 前调用）。
     *
     * @return 形如 `clothing/original/12.jpg`；若 [file] 不在 images 根下返回 null
     *         （调用方应拒绝入库，而不是把野路径存进 DB）
     */
    fun toRelative(file: File): String?

    /**
     * 相对路径 → [File]（读盘/交给 Coil 时调用）。
     *
     * @param relativePath DB 里存的值；null 或空串返回 null
     * @return 合法时返回 images 根下的 File（文件**不一定存在**，调用方展示前判空）；
     *         非法路径（绝对路径、含 `..` 逃逸、越出 images 根、Windows 盘符等）返回 null
     *
     * ⚠️ 这是本模块唯一的路径注入面。旧版本照片路径若来自外部输入（如 M4-05 导入 zip
     * 内的 JSON），必须经这里校验，否则会造成任意文件读取。
     */
    fun toFile(relativePath: String?): File?

    /**
     * 清理单件衣物的图片文件（原图 + 抠图）—— W-13 **彻底删除**时调用。
     *
     * 调用方时序（外键约束，必须遵守）：`outfit_item_cross_ref` 的 clothingItemId 外键
     * 是 NO_ACTION，故 **先**清 cross ref（`OutfitDao.deleteCrossRefsByClothingItem`），
     * **再**删衣物行，最后调本方法清文件。
     *
     * 幂等：文件不存在时静默返回。软删除场景**不要**调用本方法（见接口 KDoc 表格）。
     */
    suspend fun deleteClothingImages(id: Long)

    /**
     * 清理某穿搭的照片目录（`outfit/{id}/` 整棵删掉）—— M1-20 删除穿搭时调用。
     *
     * 幂等。删除中间某一张照片（M1-18）不使用本方法，请按目标 index 定向删除。
     */
    suspend fun deleteOutfitImages(outfitId: Long)

    /** 清理衣柜封面。幂等。删除衣柜（W-02）时调用；衣物去向另按弹层处理。 */
    suspend fun deleteWardrobeCover(wardrobeId: Long)

    /**
     * 删除穿搭照片中的**单张**（M1-18 移除照片、M4-06 清理原图）。
     *
     * 只删 index 指向的那一个文件，不动目录内其他照片；补位重排由调用方负责。
     */
    suspend fun deleteOutfitPhoto(outfitId: Long, index: Int)

    /**
     * 删除衣物**原图**但保留抠图（M4-06「清理原图只留抠图」的省空间选项，§10）。
     *
     * 单独列出而不是让调用方拼路径：是为了把「只能清有抠图的」这条规则收在一处，
     * 否则每个调用点都要重复判断抠图在不在，容易清出没有抠图的裸衣。
     */
    suspend fun deleteClothingOriginalOnly(id: Long)
}
