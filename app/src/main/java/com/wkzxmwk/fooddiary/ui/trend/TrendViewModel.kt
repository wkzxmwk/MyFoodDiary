package com.wkzxmwk.fooddiary.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wkzxmwk.fooddiary.domain.BodyMetricsCalculator
import com.wkzxmwk.fooddiary.domain.model.AppSettings
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

enum class TrendRange(val label: String, val days: Long?) {
    DAYS_7("7天", 7),
    DAYS_30("30天", 30),
    DAYS_90("90天", 90),
    DAYS_120("120天", 120),
    DAYS_180("180天", 180),
    ALL("全部", null),
}

data class WeightPoint(val date: LocalDate, val weight: Double)

data class TrendUiState(
    val range: TrendRange = TrendRange.DAYS_7,
    val points: List<WeightPoint> = emptyList(),
    val rangeStart: LocalDate = LocalDate.now().minusDays(6),
    val rangeEnd: LocalDate = LocalDate.now(),
    val currentWeight: Double? = null,
    val currentWeightSource: String = "暂无记录",
    val heightCm: Double? = null,
    val bmi: Double? = null,
    val sevenDayChange: Double? = null,
    val monthlyAverage: Double? = null,
    val targetWeight: Double? = null,
    val recordedDays: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class TrendViewModel(
    repository: FoodDiaryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val range = MutableStateFlow(TrendRange.DAYS_7)
    val uiState: StateFlow<TrendUiState> = combine(repository.observeAll(), settingsRepository.settings, range) { records, settings, selected ->
        calculateState(records, settings, selected)
    }.catch { emit(TrendUiState(isLoading = false, errorMessage = it.message ?: "读取趋势数据失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendUiState())

    fun selectRange(value: TrendRange) { range.value = value }

    private fun calculateState(records: List<DailyRecord>, settings: AppSettings, selected: TrendRange): TrendUiState {
        val today = LocalDate.now()
        val ascending = records.filter { !it.date.isAfter(today) }.sortedBy { it.date }
        val allPoints = ascending.mapNotNull { record -> record.averageWeight?.let { WeightPoint(record.date, it) } }
        val start = selected.days?.let { today.minusDays(it - 1) }
            ?: allPoints.firstOrNull()?.date
            ?: today
        val visible = allPoints.filter { point ->
            !point.date.isBefore(start) && !point.date.isAfter(today)
        }
        val latestRecord = ascending.lastOrNull { it.averageWeight != null || it.eveningWeight != null || it.morningWeight != null }
        val current = latestRecord?.averageWeight ?: latestRecord?.eveningWeight ?: latestRecord?.morningWeight
        val source = when {
            latestRecord == null -> "暂无记录"
            latestRecord.averageWeight != null -> "${latestRecord.date} 日平均"
            latestRecord.eveningWeight != null -> "${latestRecord.date} 晚上体重"
            else -> "${latestRecord.date} 早晨体重"
        }
        val sevenPoints = allPoints.filter { !it.date.isBefore(today.minusDays(6)) }
        val sevenChange = if (sevenPoints.size >= 2) sevenPoints.last().weight - sevenPoints.first().weight else null
        val monthPoints = allPoints.filter { it.date.year == today.year && it.date.month == today.month }
        return TrendUiState(
            range = selected,
            points = visible,
            rangeStart = start,
            rangeEnd = today,
            currentWeight = current,
            currentWeightSource = source,
            heightCm = settings.heightCm,
            bmi = BodyMetricsCalculator.bmi(current, settings.heightCm),
            sevenDayChange = sevenChange,
            monthlyAverage = monthPoints.takeIf { it.isNotEmpty() }?.map { it.weight }?.average(),
            targetWeight = settings.targetWeight,
            recordedDays = ascending.count { it.morningWeight != null || it.eveningWeight != null },
            isLoading = false,
        )
    }
}

class TrendViewModelFactory(
    private val repository: FoodDiaryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TrendViewModel(repository, settingsRepository) as T
}
