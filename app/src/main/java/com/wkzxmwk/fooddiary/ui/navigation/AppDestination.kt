package com.wkzxmwk.fooddiary.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Today("today", "今日", Icons.Default.Home),
    History("history", "历史", Icons.Default.DateRange),
    Trend("trend", "趋势", Icons.AutoMirrored.Filled.ShowChart),
    Settings("settings", "设置", Icons.Default.Settings),
}
