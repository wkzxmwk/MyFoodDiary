package com.wkzxmwk.fooddiary.ui.trend

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wkzxmwk.fooddiary.ui.common.EmptyContent
import com.wkzxmwk.fooddiary.ui.common.LoadingContent
import com.wkzxmwk.fooddiary.ui.common.SectionCard
import com.wkzxmwk.fooddiary.ui.theme.HealthGreen
import com.wkzxmwk.fooddiary.ui.theme.HealthGreenLight
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import androidx.compose.ui.tooling.preview.Preview
import com.wkzxmwk.fooddiary.ui.theme.FoodDiaryTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun TrendScreen(state: TrendUiState, selectRange: (TrendRange) -> Unit) {
    if (state.isLoading) { LoadingContent(); return }
    if (state.errorMessage != null) { EmptyContent("趋势加载失败", state.errorMessage); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("🌿 体重趋势", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrendRange.entries.toList().chunked(3).forEach { ranges ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ranges.forEach { range ->
                        TextButton(
                            onClick = { selectRange(range) },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (state.range == range) HealthGreen else HealthGreenLight,
                                    RoundedCornerShape(24.dp),
                                ),
                        ) {
                            Text(
                                range.label,
                                color = if (state.range == range) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("日平均体重（kg）", style = MaterialTheme.typography.titleMedium)
                when {
                    state.points.isEmpty() -> EmptyContent("暂无趋势数据", "早晚体重都记录后才会生成日平均")
                    else -> {
                        WeightChart(
                            points = state.points,
                            rangeStart = state.rangeStart,
                            rangeEnd = state.rangeEnd,
                            modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp),
                        )
                        if (state.points.size == 1) Text("至少需要两天日平均体重才能观察变化", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("当前体重", format(state.currentWeight), state.currentWeightSource, Modifier.weight(1f))
            StatCard("BMI", formatBmi(state.bmi), bmiHint(state), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("最近7天变化", signed(state.sevenDayChange), changeHint(state.sevenDayChange), Modifier.weight(1f))
            StatCard("本月平均", format(state.monthlyAverage), "按有效日平均计算", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("目标体重", format(state.targetWeight), state.targetWeight?.let { "可在设置中修改" } ?: "尚未设置", Modifier.weight(1f))
            StatCard("记录天数", "${state.recordedDays} 天", "至少有一次体重测量", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeightChart(
    points: List<WeightPoint>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    modifier: Modifier,
) {
    val minWeight = points.minOf { it.weight }
    val maxWeight = points.maxOf { it.weight }
    val tickStep = niceTickStep(max(maxWeight - minWeight, 1.0) / 4.0)
    val lower = floor((minWeight - tickStep) / tickStep) * tickStep
    val upper = ceil((maxWeight + tickStep) / tickStep) * tickStep
    val yTickCount = ((upper - lower) / tickStep).roundToLong().toInt().coerceAtLeast(1)
    val totalDays = ChronoUnit.DAYS.between(rangeStart, rangeEnd).coerceAtLeast(0)
    val xTickCount = when {
        totalDays == 0L -> 1
        totalDays <= 6L -> (totalDays + 1).toInt()
        totalDays <= 30L -> 5
        else -> 6
    }
    val xTickDates = if (xTickCount == 1) {
        listOf(rangeStart)
    } else {
        (0 until xTickCount).map { index ->
            rangeStart.plusDays((totalDays.toDouble() * index / (xTickCount - 1)).roundToLong())
        }.distinct()
    }
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val axis = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val labelTextSize = with(LocalDensity.current) { 11.sp.toPx() }
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")
    Canvas(modifier) {
        val left = 48.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = labelTextSize
        }

        repeat(yTickCount + 1) { index ->
            val value = lower + tickStep * index
            val y = bottom - (bottom - top) * index / yTickCount.toFloat()
            drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            drawLine(axis, Offset(left - 4.dp.toPx(), y), Offset(left, y), strokeWidth = 1.dp.toPx())
            labelPaint.textAlign = Paint.Align.RIGHT
            val labelBaseline = y - (labelPaint.ascent() + labelPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", value),
                left - 7.dp.toPx(),
                labelBaseline,
                labelPaint,
            )
        }
        drawLine(axis, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
        drawLine(axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

        val proportionalRangeDays = max(totalDays.toFloat(), 1f)
        xTickDates.forEach { date ->
            val elapsedDays = ChronoUnit.DAYS.between(rangeStart, date).toFloat()
            val x = left + (right - left) * (elapsedDays / proportionalRangeDays).coerceIn(0f, 1f)
            drawLine(axis, Offset(x, bottom), Offset(x, bottom + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            val label = date.format(dateFormatter)
            labelPaint.textAlign = Paint.Align.CENTER
            val halfWidth = labelPaint.measureText(label) / 2f
            val safeX = x.coerceIn(halfWidth, size.width - halfWidth)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                safeX,
                bottom + 18.dp.toPx(),
                labelPaint,
            )
        }

        fun offset(point: WeightPoint): Offset {
            val elapsedDays = ChronoUnit.DAYS.between(rangeStart, point.date).toFloat()
            val x = left + (right - left) * (elapsedDays / proportionalRangeDays).coerceIn(0f, 1f)
            val y = bottom - ((point.weight - lower) / max(upper - lower, 0.1)).toFloat() * (bottom - top)
            return Offset(x, y)
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val pointOffset = offset(point)
            if (index == 0) path.moveTo(pointOffset.x, pointOffset.y) else path.lineTo(pointOffset.x, pointOffset.y)
        }
        drawPath(path, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { point ->
            val pointOffset = offset(point)
            drawCircle(Color.White, 5.dp.toPx(), pointOffset)
            drawCircle(primary, 4.dp.toPx(), pointOffset)
        }
    }
}

private fun niceTickStep(rawStep: Double): Double {
    if (rawStep <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalized = rawStep / magnitude
    val niceNormalized = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceNormalized * magnitude
}

@Composable
private fun StatCard(title: String, value: String, hint: String, modifier: Modifier) {
    SectionCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun format(value: Double?) = value?.let { String.format(Locale.US, "%.1f kg", it) } ?: "暂无"
private fun formatBmi(value: Double?) = value?.let { String.format(Locale.US, "%.1f", it) } ?: "暂无"
private fun signed(value: Double?) = value?.let { String.format(Locale.US, "%+.1f kg", it) } ?: "暂无"
private fun bmiHint(state: TrendUiState) = when {
    state.heightCm == null -> "请先在设置中填写身高"
    state.currentWeight == null -> "暂无当前体重"
    else -> String.format(Locale.US, "按身高 %.1f cm 计算", state.heightCm)
}
private fun changeHint(value: Double?) = when { value == null -> "有效数据不足"; value < 0 -> "较窗口起点下降"; value > 0 -> "较窗口起点上升"; else -> "与窗口起点持平" }

@Preview(showBackground = true, widthDp = 390, heightDp = 820, locale = "zh-rCN")
@Composable
private fun TrendScreenPreview() {
    val today = LocalDate.now()
    FoodDiaryTheme {
        TrendScreen(
            TrendUiState(
                points = (6 downTo 0).map { WeightPoint(today.minusDays(it.toLong()), 68.0 + it * 0.2) },
                currentWeight = 68.0,
                currentWeightSource = "今日日平均",
                heightCm = 172.0,
                bmi = 23.0,
                sevenDayChange = -1.2,
                monthlyAverage = 68.4,
                targetWeight = 62.0,
                recordedDays = 7,
                isLoading = false,
            ),
            {},
        )
    }
}
