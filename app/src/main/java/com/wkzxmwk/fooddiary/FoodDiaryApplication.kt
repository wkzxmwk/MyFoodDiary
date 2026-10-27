package com.wkzxmwk.fooddiary

import android.app.Application
import androidx.room.Room
import com.wkzxmwk.fooddiary.data.local.FoodDiaryDatabase
import com.wkzxmwk.fooddiary.data.image.LocalImageManager
import com.wkzxmwk.fooddiary.data.backup.BackupManager
import com.wkzxmwk.fooddiary.data.preferences.SettingsRepositoryImpl
import com.wkzxmwk.fooddiary.data.repository.FoodDiaryRepositoryImpl
import com.wkzxmwk.fooddiary.data.reminder.ReminderScheduler
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FoodDiaryApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            this,
            FoodDiaryDatabase::class.java,
            "food_diary.db",
        ).build()
        val foodRepository = FoodDiaryRepositoryImpl(database.dailyRecordDao(), database.mealPhotoDao())
        val settingsRepository = SettingsRepositoryImpl(this)
        val reminderScheduler = ReminderScheduler(this)
        container = AppContainer(
            foodDiaryRepository = foodRepository,
            settingsRepository = settingsRepository,
            database = database,
            imageManager = LocalImageManager(this),
            backupManager = BackupManager(this, database, foodRepository, settingsRepository),
            reminderScheduler = reminderScheduler,
        )
        applicationScope.launch {
            settingsRepository.settings.collectLatest(reminderScheduler::applySettings)
        }
    }
}

data class AppContainer(
    val foodDiaryRepository: FoodDiaryRepository,
    val settingsRepository: SettingsRepository,
    val database: FoodDiaryDatabase,
    val imageManager: LocalImageManager,
    val backupManager: BackupManager,
    val reminderScheduler: ReminderScheduler,
)
