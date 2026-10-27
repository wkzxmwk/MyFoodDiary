package com.wkzxmwk.fooddiary.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wkzxmwk.fooddiary.data.backup.BackupManager
import com.wkzxmwk.fooddiary.data.image.LocalImageManager
import com.wkzxmwk.fooddiary.domain.model.AppSettings
import com.wkzxmwk.fooddiary.domain.model.Gender
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val foodRepository: FoodDiaryRepository,
    private val imageManager: LocalImageManager,
    private val backupManager: BackupManager,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, working, message) { settings, busy, text ->
        SettingsUiState(settings, busy, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTargetWeight(value: Double?) = runWork("目标体重已保存") { settingsRepository.setTargetWeight(value) }
    fun setHeightCm(value: Double?) = runWork("身高已保存") { settingsRepository.setHeightCm(value) }
    fun setGender(value: Gender?) = runWork("性别信息已保存") { settingsRepository.setGender(value) }
    fun setRemindersEnabled(enabled: Boolean) =
        runWork(if (enabled) "提醒已开启" else "提醒已关闭") {
            settingsRepository.setRemindersEnabled(enabled)
        }
    fun setReminderTime(type: ReminderType, time: LocalTime) =
        runWork("${type.displayName}时间已更新") { settingsRepository.setReminderTime(type, time) }
    fun setImageQuality(value: Int) = runWork("图片质量已更新") { settingsRepository.setImageQuality(value) }
    fun export(uri: Uri) = runWork("数据已导出") { backupManager.exportTo(uri) }
    fun import(uri: Uri) = runWork("备份已导入") { backupManager.importFrom(uri) }
    fun clearAll() = runWork("全部本地数据已清除") {
        foodRepository.deleteAll(); imageManager.clearAll(); settingsRepository.reset()
    }
    fun consumeMessage() { message.value = null }

    private fun runWork(success: String, block: suspend () -> Unit) {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            runCatching { block() }.onSuccess { message.value = success }.onFailure { message.value = it.message ?: "操作失败" }
            working.value = false
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val foodRepository: FoodDiaryRepository,
    private val imageManager: LocalImageManager,
    private val backupManager: BackupManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(settingsRepository, foodRepository, imageManager, backupManager) as T
}
