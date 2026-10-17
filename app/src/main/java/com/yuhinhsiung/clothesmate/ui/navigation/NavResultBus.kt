package com.yuhinhsiung.clothesmate.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航结果总线 —— review B-01/B-04 的修复载体。
 *
 * ## 为什么不用 `previousBackStackEntry.savedStateHandle`
 *
 * 该机制在本工程**三处独立实例真机实测全部失效**（review `2026-10-17_1015_review.md`
 * B-01 的三实例交叉定位表：选择器回传、编辑保存回执、拍照追加照片——两侧 key 字面量
 * 一致、写入/读取分属不同 destination，唯一共同点是取「上一页」；androidx navigation
 * 2.10 type-safe 路由下 `set` 写入丢失，静态读码看不出问题）。经 reviewer 认可，改为
 * **显式的 NavHost 层共享状态**：单例 StateFlow 总线，发送方在 pop 前发布、接收方
 * ViewModel（在返回栈下始终存活）即时收集并消费。
 *
 * ## 语义与边界
 *
 * - **即发即收**：StateFlow 语义保证接收方（无论订阅先后）都能拿到最近一次发布值；
 *   接收方 apply 后必须 consume（置空），防止陈旧结果被下一次进入误用。
 * - **生命周期边界**：进程死亡会丢结果（内存态）——回传都是单步交互的暂态，
 *   进程恰好死在两页之间的概率可忽略；表单主体状态经 VM 保留，不受影响。
 * - **发布时接收方必在返回栈**：选择器 / 拍照页只能从表单进入，编辑表单只能从详情
 *   进入，故不存在「发布了没人收」的悬空结果。
 */
@Singleton
class NavResultBus @Inject constructor() {

    // ── 衣物选择器（M1-18 / B-19）──────────────────────────────

    /** 表单 → 选择器：进入时预填当前已选（含顺序）。null = 无待消费种子 */
    private val _pickerSeed = MutableStateFlow<List<Long>?>(null)

    /** 选择器 → 表单：最终选择（含顺序，完整列表替换表单选择） */
    private val _pickedItemIds = MutableStateFlow<List<Long>?>(null)

    fun publishPickerSeed(selectedIds: List<Long>) {
        _pickerSeed.value = selectedIds
    }

    fun pickerSeed(): StateFlow<List<Long>?> = _pickerSeed.asStateFlow()

    fun consumePickerSeed() {
        _pickerSeed.value = null
    }

    fun publishPickedItemIds(ids: List<Long>) {
        _pickedItemIds.value = ids
    }

    fun pickedItemIds(): StateFlow<List<Long>?> = _pickedItemIds.asStateFlow()

    fun consumePickedItemIds() {
        _pickedItemIds.value = null
    }

    // ── 拍照追加（M1-18 确认页内加拍） ──────────────────────────

    /** 拍照页 → 穿搭表单：追加照片的 Uri 字符串 */
    private val _appendCapturedUri = MutableStateFlow<String?>(null)

    fun publishAppendCaptured(uri: String) {
        _appendCapturedUri.value = uri
    }

    fun appendCapturedUri(): StateFlow<String?> = _appendCapturedUri.asStateFlow()

    fun consumeAppendCaptured() {
        _appendCapturedUri.value = null
    }

    // ── 编辑保存回执（M1-12 / M1-18 编辑模式）──────────────────

    /** 衣物编辑表单 → 衣物详情：「已保存」Snackbar 回执 */
    private val _clothingSaved = MutableStateFlow(false)

    /** 穿搭编辑表单 → 穿搭详情：「已保存」Snackbar 回执 */
    private val _outfitSaved = MutableStateFlow(false)

    fun publishClothingSaved() {
        _clothingSaved.value = true
    }

    fun clothingSaved(): StateFlow<Boolean> = _clothingSaved.asStateFlow()

    fun consumeClothingSaved() {
        _clothingSaved.value = false
    }

    fun publishOutfitSaved() {
        _outfitSaved.value = true
    }

    fun outfitSaved(): StateFlow<Boolean> = _outfitSaved.asStateFlow()

    fun consumeOutfitSaved() {
        _outfitSaved.value = false
    }
}
