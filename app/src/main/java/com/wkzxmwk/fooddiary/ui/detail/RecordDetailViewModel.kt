package com.wkzxmwk.fooddiary.ui.detail

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wkzxmwk.fooddiary.data.image.LocalImageManager
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.domain.model.MealType
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecordDetailUiState(
    val record: DailyRecord? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val deleted: Boolean = false,
    val errorMessage: String? = null,
)

class RecordDetailViewModel(
    private val date: LocalDate,
    private val repository: FoodDiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val imageManager: LocalImageManager,
) : ViewModel() {
    private val imageImportMutex = Mutex()
    private val saving = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    val uiState: StateFlow<RecordDetailUiState> = combine(repository.observeRecord(date), saving, deleted, error) { record, busy, wasDeleted, message ->
        RecordDetailUiState(record, false, busy, wasDeleted, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordDetailUiState())

    fun createCameraUri() = imageManager.createCameraUri()
    suspend fun loadCropPreview(uri: Uri): Bitmap = imageManager.loadCropPreview(uri)
    fun updateWeights(morning: Double?, evening: Double?) = launchSaving("保存体重失败") { repository.updateWeights(date, morning, evening) }
    fun updateNote(note: String) = launchSaving("保存备注失败") { repository.updateNote(date, note) }
    fun addCroppedPhoto(meal: MealType, source: Uri, bitmap: Bitmap) {
        viewModelScope.launch {
            imageImportMutex.withLock {
                saving.value = true
                error.value = null
                runCatching {
                    val quality = settingsRepository.settings.first().imageQuality
                    val path = imageManager.importCroppedImage(source, bitmap, date, quality)
                    runCatching { repository.addPhoto(MealPhoto(UUID.randomUUID().toString(), date, meal, path, System.currentTimeMillis())) }
                        .onFailure { imageManager.delete(path) }.getOrThrow()
                }.onFailure {
                    bitmap.takeUnless(Bitmap::isRecycled)?.recycle()
                    error.value = it.message ?: "添加照片失败"
                }
                saving.value = false
            }
        }
    }
    fun discardPendingImage(uri: Uri) { viewModelScope.launch { imageManager.discardPendingImage(uri) } }
    fun deletePhoto(photo: MealPhoto) = launchSaving("删除照片失败") {
        check(imageManager.delete(photo.localPath)) { "无法删除本地图片" }
        repository.deletePhotoRecord(photo.id)
    }
    fun deleteRecord() = launchSaving("删除记录失败") {
        repository.getPhotos(date).forEach { check(imageManager.delete(it.localPath)) { "无法删除本地图片" } }
        repository.deleteRecord(date)
        deleted.value = true
    }

    private fun launchSaving(message: String, block: suspend () -> Unit) {
        if (saving.value) return
        viewModelScope.launch {
            saving.value = true
            error.value = null
            runCatching { block() }.onFailure { error.value = it.message ?: message }
            saving.value = false
        }
    }
}

class RecordDetailViewModelFactory(
    private val date: LocalDate,
    private val repository: FoodDiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val imageManager: LocalImageManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecordDetailViewModel(date, repository, settingsRepository, imageManager) as T
}
