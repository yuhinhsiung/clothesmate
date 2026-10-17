package com.yuhinhsiung.clothesmate.core.image

import android.net.Uri

/**
 * 图片保存入口 —— 供 M1-06（衣柜封面）、M1-14（录入原图）等需要「选图 → 压缩 → 落盘」的界面使用。
 *
 * ## 与 M1-03 的分工
 *
 * M1-03 的 [ImageStorage] + [ImageCompressor] 只回答「按 §3.3 规范怎么存、怎么压」，
 * 不涉及「id 从哪来」。而录衣服（M1-14）的时序是「先选图、后分配编号」——照片必须在
 * id 分配**之前**落盘，保存时再按 `clothing/original/{id}.jpg` 归位。
 * 这段「暂存 → 归位」的编排由本类负责（用户 2026-10-15 拍板：M1-03 严格按任务字面做，
 * 这块归后续任务，这里落地）。
 */
interface ImageStore {

    /**
     * 把选中的图压进**暂存区**，返回暂存文件（**相对路径**，可先展示、后再决定去向）。
     *
     * 暂存区是 `cacheDir/tmp_images/`（非 `filesDir/images/`）：
     * 1. 它不该参与导出备份（M4-05）——只是中间产物；
     * 2. 系统清理缓存时自动回收，不会像 filesDir 那样永久堆积；
     * 3. 与 §3.3 的正式目录物理隔离，杜绝「半成品被当成正式图」。
     *
     * @return 暂存文件；失败抛 [ImageProcessException]
     */
    suspend fun stash(uri: Uri): StagedImage

    /**
     * 把暂存图归位到正式位置（[ImageStorage.clothingOriginalFile] 等）。
     *
     * ⚠️ 本操作**会消费掉暂存源**（rename 优先，跨卷退化为复制后删源）。
     * 多张图批量归位时，前面成功的图片源已经没了——中途失败要让草稿仍可重试，
     * 必须用 [restoreStaged] 把已归位的那几张退回去。
     *
     * @param staged [stash] 的产物
     * @param destination 目标绝对路径（由 [ImageStorage] 的方法给出）
     * @return 最终文件的**相对路径**，可直接写库
     */
    suspend fun promote(staged: StagedImage, destination: java.io.File): String

    /**
     * 归位失败后的**补偿**：把已经落到 [destination] 的文件退回暂存位置（review R-14）。
     *
     * 为什么必须有：保存流程是「先把一批暂存图 promote 到正式目录 → 再写库」。第 2 张
     * 归位失败时，第 1 张的暂存源已经被 consume 掉了，而补偿逻辑又会删掉已归位的文件；
     * 草稿里的 `StagedImage` 于是指向一个**不存在的文件**，用户按提示重试仍然失败，
     * 只能放弃重录。退回暂存区后，草稿恢复成「什么都没发生」的可重试状态。
     *
     * 幂等：源文件还在（说明该张还没归位）或目标不存在时都不做事。
     */
    suspend fun restoreStaged(staged: StagedImage, destination: java.io.File)

    /** 清掉单个暂存图 */
    suspend fun clearStaged(staged: StagedImage)
}

/**
 * 暂存图。
 *
 * @property file 暂存文件（绝对路径，仅本进程内使用，**不要**写库）
 * @property byteSize 压缩后字节数，用于展示与排错
 */
data class StagedImage(
    val file: java.io.File,
    val byteSize: Long,
)
