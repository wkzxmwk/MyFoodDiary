package com.wkzxmwk.fooddiary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = HealthGreen,
    onPrimary = Color.White,
    primaryContainer = HealthGreenLight,
    onPrimaryContainer = HealthGreenDark,
    secondary = EveningBlue,
    tertiary = MorningOrange,
    background = AppBackground,
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    outline = CardBorder,
    error = Color(0xFFC62828),
)

@Composable
fun FoodDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = FoodDiaryTypography,
        content = content,
    )
}

