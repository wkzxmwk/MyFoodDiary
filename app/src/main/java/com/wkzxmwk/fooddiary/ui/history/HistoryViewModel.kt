package com.wkzxmwk.fooddiary.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class HistoryPeriod(val label: String) {
    WEEK("本周"),
    MONTH("本月"),
    CUSTOM("自定义"),
}

data class HistoryUiState(
    val period: HistoryPeriod = HistoryPeriod.WEEK,
    val selectedDate: LocalDate = LocalDate.now(),
    val startDate: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val endDate: LocalDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
    val records: List<DailyRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: FoodDiaryRepository) : ViewModel() {
    private data class Selection(
        val period: HistoryPeriod = HistoryPeriod.WEEK,
        val anchorDate: LocalDate = LocalDate.now(),
        val customStart: LocalDate = LocalDate.now().minusDays(6),
        val customEnd: LocalDate = LocalDate.now(),
    )

    private val selection = MutableStateFlow(Selection())
    val uiState: StateFlow<HistoryUiState> = selection.flatMapLatest { selected ->
        val (startDate, endDate) = selected.range()
        repository.observeRange(startDate, endDate)
            .map { records ->
                HistoryUiState(
                    period = selected.period,
                    selectedDate = selected.anchorDate,
                    startDate = startDate,
                    endDate = endDate,
                    records = records.filterNot { it.isEmpty }.sortedByDescending { it.date },
                    isLoading = false,
                )
            }
            .catch {
                emit(
                    HistoryUiState(
                        period = selected.period,
                        selectedDate = selected.anchorDate,
                        startDate = startDate,
                        endDate = endDate,
                        isLoading = false,
                        errorMessage = it.message ?: "读取历史记录失败",
                    ),
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectPeriod(period: HistoryPeriod) {
        if (period != HistoryPeriod.CUSTOM) {
            selection.value = selection.value.copy(period = period, anchorDate = LocalDate.now())
        }
    }

    fun previousPeriod() = movePeriod(-1)
    fun nextPeriod() = movePeriod(1)

    fun selectDate(date: LocalDate) {
        selection.value = selection.value.copy(anchorDate = date)
    }

    fun selectCustomRange(startDate: LocalDate, endDate: LocalDate) {
        val normalizedStart = minOf(startDate, endDate)
        val normalizedEnd = maxOf(startDate, endDate)
        selection.value = selection.value.copy(
            period = HistoryPeriod.CUSTOM,
            anchorDate = normalizedEnd,
            customStart = normalizedStart,
            customEnd = normalizedEnd,
        )
    }

    private fun movePeriod(offset: Long) {
        val current = selection.value
        val targetDate = when (current.period) {
            HistoryPeriod.WEEK -> current.anchorDate.plusWeeks(offset)
            HistoryPeriod.MONTH -> {
                val targetMonth = YearMonth.from(current.anchorDate).plusMonths(offset)
                targetMonth.atDay(current.anchorDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
            }
            HistoryPeriod.CUSTOM -> return
        }
        selection.value = current.copy(anchorDate = targetDate)
    }

    private fun Selection.range(): Pair<LocalDate, LocalDate> = when (period) {
        HistoryPeriod.WEEK -> {
            val monday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }
        HistoryPeriod.MONTH -> YearMonth.from(anchorDate).let { it.atDay(1) to it.atEndOfMonth() }
        HistoryPeriod.CUSTOM -> customStart to customEnd
    }
}

class HistoryViewModelFactory(private val repository: FoodDiaryRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(repository) as T
}
