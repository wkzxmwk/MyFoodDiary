package com.wkzxmwk.fooddiary.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wkzxmwk.fooddiary.domain.BodyMetricsCalculator
import com.wkzxmwk.fooddiary.domain.WeightCalculator
import com.wkzxmwk.fooddiary.domain.model.AppSettings
import com.wkzxmwk.fooddiary.domain.model.Gender
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    private object Keys {
        val targetWeight = doublePreferencesKey("target_weight")
        val heightCm = doublePreferencesKey("height_cm")
        val gender = stringPreferencesKey("gender")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val notificationPermissionRequested = booleanPreferencesKey("notification_permission_requested")
        val breakfastReminderTime = stringPreferencesKey("breakfast_reminder_time")
        val lunchReminderTime = stringPreferencesKey("lunch_reminder_time")
        val dinnerReminderTime = stringPreferencesKey("dinner_reminder_time")
        val imageQuality = intPreferencesKey("image_quality")
        val weightUnit = stringPreferencesKey("weight_unit")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map(::toSettings)

    override suspend fun setTargetWeight(weight: Double?) {
        require(weight == null || WeightCalculator.isValid(weight)) { "目标体重应在20至300kg之间" }
        context.settingsDataStore.edit { preferences ->
            if (weight == null) preferences.remove(Keys.targetWeight)
            else preferences[Keys.targetWeight] = WeightCalculator.normalize(weight)
        }
    }

    override suspend fun setImageQuality(quality: Int) {
        require(quality in 50..95) { "图片质量应在50至95之间" }
        context.settingsDataStore.edit { it[Keys.imageQuality] = quality }
    }

    override suspend fun setHeightCm(heightCm: Double?) {
        require(heightCm == null || BodyMetricsCalculator.isValidHeight(heightCm)) {
            "身高应在50至250cm之间"
        }
        context.settingsDataStore.edit { preferences ->
            if (heightCm == null) preferences.remove(Keys.heightCm)
            else preferences[Keys.heightCm] = BodyMetricsCalculator.normalizeHeight(heightCm)
        }
    }

    override suspend fun setGender(gender: Gender?) {
        context.settingsDataStore.edit { preferences ->
            if (gender == null) preferences.remove(Keys.gender)
            else preferences[Keys.gender] = gender.name
        }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.remindersEnabled] = enabled }
    }

    override suspend fun setNotificationPermissionRequested(requested: Boolean) {
        context.settingsDataStore.edit { it[Keys.notificationPermissionRequested] = requested }
    }

    override suspend fun setReminderTime(type: ReminderType, time: LocalTime) {
        val key = when (type) {
            ReminderType.BREAKFAST -> Keys.breakfastReminderTime
            ReminderType.LUNCH -> Keys.lunchReminderTime
            ReminderType.DINNER -> Keys.dinnerReminderTime
        }
        context.settingsDataStore.edit { it[key] = time.withSecond(0).withNano(0).toString() }
    }

    override suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun toSettings(preferences: Preferences) = AppSettings(
        targetWeight = preferences[Keys.targetWeight],
        heightCm = preferences[Keys.heightCm],
        gender = preferences[Keys.gender]?.let { stored ->
            runCatching { Gender.valueOf(stored) }.getOrNull()
        },
        remindersEnabled = preferences[Keys.remindersEnabled] ?: true,
        notificationPermissionRequested = preferences[Keys.notificationPermissionRequested] ?: false,
        breakfastReminderTime = preferences.readTime(Keys.breakfastReminderTime, ReminderType.BREAKFAST.defaultTime),
        lunchReminderTime = preferences.readTime(Keys.lunchReminderTime, ReminderType.LUNCH.defaultTime),
        dinnerReminderTime = preferences.readTime(Keys.dinnerReminderTime, ReminderType.DINNER.defaultTime),
        imageQuality = preferences[Keys.imageQuality] ?: 80,
        weightUnit = preferences[Keys.weightUnit] ?: "kg",
    )
}

private fun Preferences.readTime(key: Preferences.Key<String>, default: LocalTime): LocalTime =
    this[key]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default
