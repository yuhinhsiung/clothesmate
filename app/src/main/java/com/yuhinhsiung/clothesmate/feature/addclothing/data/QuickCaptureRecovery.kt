package com.yuhinhsiung.clothesmate.feature.addclothing.data

import android.content.Context
import androidx.work.WorkManager
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import com.yuhinhsiung.clothesmate.core.datastore.QuickCaptureAiSuggestionStore
import com.yuhinhsiung.clothesmate.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 进程被杀后的连拍恢复。
 *
 * WorkManager 在进程被强制停止时可能留下 RUNNING 的 WorkSpec，单靠它自身
 * 不会重跑那一件。入队时写入的 pending 标记是这里的事实来源：启动时取消旧队列，
 * 对仍缺裁图的 id 重新入队；已删除或已有裁图的标记直接清理。
 */
@Singleton
class QuickCaptureRecovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: QuickCaptureAiSuggestionStore,
    private val repository: ClothingRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun recover() {
        scope.launch {
            val pending = store.pendingIds()
            // 冷启动时旧进程已不存在，队列里残留的 RUNNING 一定不可信，先整体取消。
            runCatching {
                WorkManager.getInstance(context)
                    .cancelUniqueWork(QuickCaptureWorker.QUEUE_NAME)
                    .result
                    .get(5, TimeUnit.SECONDS)
            }
            if (pending.isEmpty()) return@launch
            pending.forEach { id ->
                val item = repository.getItem(id)
                if (item == null || item.cutoutImagePath != null) {
                    store.clearPending(id)
                } else {
                    QuickCaptureWorker.enqueue(context, id)
                }
            }
        }
    }
}
