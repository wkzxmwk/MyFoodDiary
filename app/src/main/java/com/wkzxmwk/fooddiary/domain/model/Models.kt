package com.wkzxmwk.fooddiary.domain.model

import com.wkzxmwk.fooddiary.domain.WeightCalculator
import java.time.LocalDate
import java.time.LocalTime

data class MealPhoto(
    val id: String,
    val recordDate: LocalDate,
    val mealType: MealType,
    val localPath: String,
    val createdAt: Long,
)

data class DailyRecord(
    val date: LocalDate,
    val morningWeight: Double? = null,
    val eveningWeight: Double? = null,
    val note: String = "",
    val photos: List<MealPhoto> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val averageWeight: Double?
        get() = WeightCalculator.average(morningWeight, eveningWeight)

    val isEmpty: Boolean
        get() = morningWeight == null && eveningWeight == null && note.isBlank() && photos.isEmpty()
}

data class AppSettings(
    val targetWeight: Double? = null,
    val heightCm: Double? = null,
    val gender: Gender? = null,
    val remindersEnabled: Boolean = true,
    val notificationPermissionRequested: Boolean = false,
    val breakfastReminderTime: LocalTime = ReminderType.BREAKFAST.defaultTime,
    val lunchReminderTime: LocalTime = ReminderType.LUNCH.defaultTime,
    val dinnerReminderTime: LocalTime = ReminderType.DINNER.defaultTime,
    val imageQuality: Int = 80,
    val weightUnit: String = "kg",
) {
    fun reminderTime(type: ReminderType): LocalTime = when (type) {
        ReminderType.BREAKFAST -> breakfastReminderTime
        ReminderType.LUNCH -> lunchReminderTime
        ReminderType.DINNER -> dinnerReminderTime
    }
}
