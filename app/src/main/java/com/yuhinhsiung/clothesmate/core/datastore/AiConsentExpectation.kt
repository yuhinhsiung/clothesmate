package com.yuhinhsiung.clothesmate.core.datastore

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 仅用于“导入失败后的补偿写”。把授权写限定在预期配置上：
 * 真正的 `setAiConsent` 会在 DataStore 事务内再次核对当前配置，若配置已被
 * 用户并发修改，就降级为 Unasked，而不是把旧授权写给新端点。
 */
internal data class AiConsentExpectation(
    val config: AiConfig,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AiConsentExpectation>
}
