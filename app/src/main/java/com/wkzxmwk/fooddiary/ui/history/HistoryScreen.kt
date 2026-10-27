package com.wkzxmwk.fooddiary.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.ui.common.EmptyContent
import com.wkzxmwk.fooddiary.ui.common.LoadingContent
import com.wkzxmwk.fooddiary.ui.common.LocalFileImage
import com.wkzxmwk.fooddiary.ui.common.SectionCard
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.tooling.preview.Preview
import com.wkzxmwk.fooddiary.ui.theme.FoodDiaryTheme
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.material3.rememberDateRangePickerState
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    selectPeriod: (HistoryPeriod) -> Unit,
    previousPeriod: () -> Unit,
    nextPeriod: () -> Unit,
    selectDate: (LocalDate) -> Unit,
    selectCustomRange: (LocalDate, LocalDate) -> Unit,
    openRecord: (DailyRecord) -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        HistoryHeader()
        Spacer(Modifier.height(8.dp))
        PeriodSelector(
            selected = state.period,
            onSelect = { period ->
                if (period == HistoryPeriod.CUSTOM) showRangePicker = true else selectPeriod(period)
            },
        )
        Spacer(Modifier.height(6.dp))
        when (state.period) {
            HistoryPeriod.WEEK -> {
                PeriodNavigator(
                    label = "${state.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} — ${state.endDate.format(DateTimeFormatter.ofPattern("M月d日"))}",
                    previous = previousPeriod,
                    next = nextPeriod,
                )
                WeekSelector(state.startDate, state.selectedDate, state.records, selectDate)
            }
            HistoryPeriod.MONTH -> {
                PeriodNavigator(
                    label = YearMonth.from(state.selectedDate).format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)),
                    previous = previousPeriod,
                    next = nextPeriod,
                )
                MonthSelector(state, selectDate)
            }
            HistoryPeriod.CUSTOM -> CustomRangeSelector(state.startDate, state.endDate) {
                showRangePicker = true
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.isLoading -> LoadingContent()
            state.errorMessage != null -> EmptyContent("加载失败", state.errorMessage)
            state.records.isEmpty() -> EmptyContent(
                when (state.period) {
                    HistoryPeriod.WEEK -> "本周暂无记录"
                    HistoryPeriod.MONTH -> "本月暂无记录"
                    HistoryPeriod.CUSTOM -> "所选区间暂无记录"
                },
                "可切换时间范围，查看其他日期的饮食和体重记录",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.records, key = { it.date.toEpochDay() }) { record -> HistoryCard(record) { openRecord(record) } }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = state.endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = pickerState.selectedStartDateMillis
                        val endMillis = pickerState.selectedEndDateMillis ?: startMillis
                        if (startMillis != null && endMillis != null) {
                            selectCustomRange(
                                Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate(),
                                Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showRangePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("取消") }
            },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("选择历史记录区间", Modifier.padding(16.dp)) },
                headline = null,
                showModeToggle = false,
            )
        }
    }
}

@Composable
private fun HistoryHeader() {
    Column(Modifier.fillMaxWidth()) {
        Text("🌿 历史记录", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text("回顾过去，见证每一点变化", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PeriodSelector(selected: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HistoryPeriod.entries.forEach { period ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (period == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(22.dp),
                        )
                        .clickable { onSelect(period) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        period.label,
                        color = if (period == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (period == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigator(label: String, previous: () -> Unit, next: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(previous, Modifier.size(36.dp)) { Icon(Icons.Default.ChevronLeft, "上一个时间段") }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(next, Modifier.size(36.dp)) { Icon(Icons.Default.ChevronRight, "下一个时间段") }
    }
}

@Composable
private fun WeekSelector(weekStart: LocalDate, selectedDate: LocalDate, records: List<DailyRecord>, selectDate: (LocalDate) -> Unit) {
    val days = (0L..6L).map(weekStart::plusDays)
    val recordDates = records.mapTo(hashSetOf()) { it.date }
    SectionCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEach { date ->
                val selected = date == selectedDate
                Column(
                    modifier = Modifier.weight(1f).clickable { selectDate(date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(weekdayShort(date.dayOfWeek), style = MaterialTheme.typography.bodyMedium)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .shadow(if (date in recordDates) 4.dp else 0.dp, CircleShape)
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary
                                    date in recordDates -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(state: HistoryUiState, selectDate: (LocalDate) -> Unit) {
    val month = YearMonth.from(state.selectedDate)
    val firstDate = month.atDay(1)
    val leadingEmptyCells = firstDate.dayOfWeek.value - 1
    val cells = List<LocalDate?>(leadingEmptyCells) { null } +
        (1..month.lengthOfMonth()).map(month::atDay)
    val paddedCells = cells + List((7 - cells.size % 7) % 7) { null }
    val recordDates = state.records.mapTo(hashSetOf()) { it.date }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                    Text(
                        weekday,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            paddedCells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f).height(28.dp))
                        } else {
                            val selected = date == state.selectedDate
                            Column(
                                modifier = Modifier.weight(1f).height(28.dp).clickable { selectDate(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .shadow(if (date in recordDates) 3.dp else 0.dp, CircleShape)
                                        .background(
                                            when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                date in recordDates -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            },
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(date.dayOfMonth.toString(), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomRangeSelector(startDate: LocalDate, endDate: LocalDate, edit: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = edit)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("自定义时间区间", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${startDate.format(DateTimeFormatter.ofPattern("yyyy/M/d"))} — ${endDate.format(DateTimeFormatter.ofPattern("yyyy/M/d"))}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text("修改", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HistoryCard(record: DailyRecord, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.date.format(DateTimeFormatter.ofPattern("M月d日")), style = MaterialTheme.typography.titleSmall)
                    Text(
                        weekday(record.date.dayOfWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (record.averageWeight != null) {
                    Text(
                        "日平均 ${String.format(Locale.US, "%.1f kg", record.averageWeight)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            val visiblePhotos = record.photos.take(4)
            if (visiblePhotos.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val gap = 3.dp
                    val thumbnailSize = (maxWidth - gap * 3) / 4
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        visiblePhotos.forEach { photo ->
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(thumbnailSize),
                            ) {
                                LocalFileImage(photo.localPath, "餐食缩略图")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun weekday(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "周一"; DayOfWeek.TUESDAY -> "周二"; DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"; DayOfWeek.FRIDAY -> "周五"; DayOfWeek.SATURDAY -> "周六"; DayOfWeek.SUNDAY -> "周日"
}

private fun weekdayShort(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "一"; DayOfWeek.TUESDAY -> "二"; DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"; DayOfWeek.FRIDAY -> "五"; DayOfWeek.SATURDAY -> "六"; DayOfWeek.SUNDAY -> "日"
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760, locale = "zh-rCN")
@Composable
private fun HistoryScreenPreview() {
    FoodDiaryTheme {
        HistoryScreen(
            HistoryUiState(records = listOf(DailyRecord(LocalDate.now(), 68.2, 67.8), DailyRecord(LocalDate.now().minusDays(1), 68.5, 68.1)), isLoading = false),
            {}, {}, {}, {}, { _, _ -> }, {},
        )
    }
}
