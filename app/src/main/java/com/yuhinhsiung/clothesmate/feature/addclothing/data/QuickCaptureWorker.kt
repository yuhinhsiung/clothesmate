package com.yuhinhsiung.clothesmate.feature.addclothing.data

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yuhinhsiung.clothesmate.core.ai.ClothingRecognizer
import com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint
import com.yuhinhsiung.clothesmate.core.datastore.AiConsent
import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.image.BackgroundRemover
import com.yuhinhsiung.clothesmate.core.image.BitmapLoader
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.LocalColorExtractorService
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.ClothingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuickCaptureWorkerEntryPoint {
    fun backgroundRemover(): BackgroundRemover
    fun imageStore(): ImageStore
    fun imageStorage(): ImageStorage
    fun clothingRepository(): ClothingRepository
    fun colorExtractor(): LocalColorExtractorService
    fun recognizer(): ClothingRecognizer
    fun bitmapLoader(): BitmapLoader
    fun settingsStore(): UserSettingsStore
}

/**
 * 快速连拍的后台处理（M4-02）。
 *
 * 建档本身在 ViewModel 的调用方作用域完成；耗时的抠图、取色和 AI 预填交给
 * WorkManager，退出页面/杀进程后仍由系统调度，避免“有行无图”的半成品。
 * 只填空字段，绝不覆盖用户后来手填的内容；AI 仅在已授权且端点完整时执行。
 */
class QuickCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val entry: QuickCaptureWorkerEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            QuickCaptureWorkerEntryPoint::class.java,
        )
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ITEM_ID, -1L)
        if (id <= 0L) return Result.success()
        process(id)
        return Result.success()
    }

    private suspend fun process(id: Long) {
        val storage = entry.imageStorage()
        val repository = entry.clothingRepository()
        var cutoutPath: String? = null
        runCatching {
            val bitmap = entry.backgroundRemover().removeBackground(
                Uri.fromFile(storage.clothingOriginalFile(id)),
            )
            try {
                val staged = entry.imageStore().stageCutout(bitmap)
                cutoutPath = entry.imageStore().promote(staged, storage.clothingCutoutFile(id))
                repository.updateCutoutPath(id, cutoutPath)
            } finally {
                bitmap.recycle()
            }
        }

        val colorUri = Uri.fromFile(
            if (cutoutPath != null) storage.clothingCutoutFile(id)
            else storage.clothingOriginalFile(id),
        )
        val colors = runCatching { entry.colorExtractor().extractKeys(colorUri) }
            .getOrDefault(emptyList())
        if (colors.isNotEmpty()) {
            val item = repository.getItem(id)
            if (item != null && item.colorKeys.isEmpty()) {
                runCatching { repository.updateItem(item.copy(colorKeys = colors)) }
            }
        }
        runCatching { applyAiSuggestion(id) }
    }

    private suspend fun applyAiSuggestion(id: Long) {
        val settings = entry.settingsStore().settings.first()
        if (settings.aiConsent != AiConsent.Granted) return
        val endpoint = RecognitionEndpoint(
            baseUrl = settings.aiConfig.baseUrl,
            apiKey = settings.aiConfig.apiKey,
            model = settings.aiConfig.model,
        )
        if (!endpoint.isConfigured) return
        val repository = entry.clothingRepository()
        val item = repository.getItem(id) ?: return
        if (item.categoryKey != null && item.styleTags.isNotEmpty() && item.seasonTags.isNotEmpty()) return
        val storage = entry.imageStorage()
        val source = storage.clothingCutoutFile(id).takeIf { it.exists() }
            ?: storage.clothingOriginalFile(id)
        val bytes = entry.bitmapLoader().jpegBytes(source, 1024) ?: return
        val result = entry.recognizer().recognize(endpoint, bytes)
        if (result.isEmpty) return
        val category = result.categoryKey?.let { ClothingCategory.fromKey(it) }?.key
        val styles = result.styleTags.filter { it.isNotBlank() }
        val seasons = result.seasonKeys.mapNotNull { raw ->
            Season.fromKey(raw) ?: Season.searchAliases[raw]
        }.map { it.key }
        if (category == null && styles.isEmpty() && seasons.isEmpty()) return
        val latest = repository.getItem(id) ?: return
        repository.updateItem(
            latest.copy(
                categoryKey = latest.categoryKey ?: category,
                styleTags = (latest.styleTags + styles).distinct(),
                seasonTags = (latest.seasonTags + seasons).distinct(),
            ),
        )
    }

    companion object {
        private const val KEY_ITEM_ID = "item_id"

        fun uniqueName(itemId: Long): String = "quick-capture-cutout-$itemId"

        fun enqueue(context: Context, itemId: Long) {
            val request = OneTimeWorkRequestBuilder<QuickCaptureWorker>()
                .setInputData(workDataOf(KEY_ITEM_ID to itemId))
                .addTag("quick-capture")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(itemId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
