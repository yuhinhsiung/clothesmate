package com.yuhinhsiung.clothesmate.feature.today.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.model.WearException
import com.yuhinhsiung.clothesmate.core.model.WearFailure
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.feature.outfit.data.OutfitRepository
import com.yuhinhsiung.clothesmate.feature.today.data.WearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class WearCalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val recordsByDate: Map<LocalDate, List<WearRecord>> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val outfits: List<Outfit> = emptyList(),
    val backfilling: Boolean = false,
) {
    val today: LocalDate get() = LocalDate.now()
    val selectedRecords: List<WearRecord>
        get() = selectedDate?.let { recordsByDate[it] }.orEmpty()
}

sealed interface WearCalendarEvent {
    data object Backfilled : WearCalendarEvent
    data class BackfillFailed(val failure: WearFailure?) : WearCalendarEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class WearCalendarViewModel @Inject constructor(
    private val wearRepository: WearRepository,
    private val outfitRepository: OutfitRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val _uiState = MutableStateFlow(WearCalendarUiState())
    val uiState: StateFlow<WearCalendarUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WearCalendarEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WearCalendarEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            month.flatMapLatest { value ->
                wearRepository.observeRecordsInMonth(value).map { records ->
                    value to records.groupBy { it.date }
                }
            }.collect { (value, byDate) ->
                _uiState.update { it.copy(month = value, recordsByDate = byDate) }
            }
        }
        viewModelScope.launch {
            outfitRepository.observeOutfits().collect { outfits ->
                _uiState.update { it.copy(outfits = outfits) }
            }
        }
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
        _uiState.update { it.copy(selectedDate = null) }
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        _uiState.update { it.copy(selectedDate = null) }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun dismissDate() {
        _uiState.update { it.copy(selectedDate = null) }
    }

    fun backfill(outfitId: Long) {
        val date = _uiState.value.selectedDate ?: return
        if (date.isAfter(LocalDate.now())) return
        if (_uiState.value.backfilling) return
        _uiState.update { it.copy(backfilling = true) }
        viewModelScope.launch {
            try {
                wearRepository.confirmWear(
                    outfitId = outfitId,
                    date = date,
                    scheduleTags = emptyList(),
                    weatherJson = null,
                    note = null,
                )
                _events.emit(WearCalendarEvent.Backfilled)
                _uiState.update { it.copy(selectedDate = null) }
            } catch (error: WearException) {
                _events.emit(WearCalendarEvent.BackfillFailed(error.failure))
            } catch (_: Exception) {
                _events.emit(WearCalendarEvent.BackfillFailed(null))
            } finally {
                _uiState.update { it.copy(backfilling = false) }
            }
        }
    }

    fun updateNote(recordId: Long, note: String) {
        viewModelScope.launch { wearRepository.updateNote(recordId, note) }
    }
}
