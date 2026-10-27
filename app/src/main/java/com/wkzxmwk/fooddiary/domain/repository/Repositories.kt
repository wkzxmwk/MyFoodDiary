package com.wkzxmwk.fooddiary.domain.repository

import com.wkzxmwk.fooddiary.domain.model.AppSettings
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.model.Gender
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

interface FoodDiaryRepository {
    fun observeRecord(date: LocalDate): Flow<DailyRecord?>
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyRecord>>
    fun observeAll(): Flow<List<DailyRecord>>
    suspend fun updateWeights(date: LocalDate, morning: Double?, evening: Double?)
    suspend fun updateNote(date: LocalDate, note: String)
    suspend fun addPhoto(photo: MealPhoto)
    suspend fun getPhoto(id: String): MealPhoto?
    suspend fun getPhotos(date: LocalDate): List<MealPhoto>
    suspend fun deletePhotoRecord(id: String)
    suspend fun deleteRecord(date: LocalDate)
    suspend fun deleteAll()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTargetWeight(weight: Double?)
    suspend fun setHeightCm(heightCm: Double?)
    suspend fun setGender(gender: Gender?)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setNotificationPermissionRequested(requested: Boolean)
    suspend fun setReminderTime(type: ReminderType, time: LocalTime)
    suspend fun setImageQuality(quality: Int)
    suspend fun reset()
}
