package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import com.yuhinhsiung.clothesmate.core.update.UpdateManager
import com.yuhinhsiung.clothesmate.core.update.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager,
) : ViewModel() {
    val state: StateFlow<UpdateUiState> = manager.state

    fun check() = manager.checkManually()
    fun download() = manager.download()
    fun cancelDownload() = manager.cancelDownload()
    fun install() = manager.install()
    fun openReleasePage() = manager.openReleasePage()
    fun openUnknownSourcesSettings() = manager.openUnknownSourcesSettings()
}
