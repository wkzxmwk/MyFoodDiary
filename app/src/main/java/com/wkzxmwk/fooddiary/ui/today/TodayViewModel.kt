package com.wkzxmwk.fooddiary.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.domain.model.MealType
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import com.wkzxmwk.fooddiary.data.image.LocalImageManager
import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val record: DailyRecord? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: FoodDiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val imageManager: LocalImageManager,
) : ViewModel() {
    private val imageImportMutex = Mutex()
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TodayUiState> = combine(
        selectedDate.flatMapLatest { date ->
            repository.observeRecord(date).catch { throwable ->
                error.value = throwable.message ?: "读取记录失败"
                emit(null)
            }
        },
        selectedDate,
        saving,
        error,
    ) { record, date, isSaving, errorMessage ->
        TodayUiState(date, record, isLoading = false, isSaving = isSaving, errorMessage = errorMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun previousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun nextDay() { selectedDate.value = selectedDate.value.plusDays(1) }
    fun selectDate(date: LocalDate) { selectedDate.value = date }
    fun syncToToday() {
        val today = LocalDate.now()
        if (selectedDate.value != today) selectedDate.value = today
    }
    fun clearError() { error.value = null }
    fun createCameraUri(): Uri = imageManager.createCameraUri()

    suspend fun createDailyShareImage(): Uri {
        val state = uiState.value
        return imageManager.createDailyShareImage(state.date, state.record?.photos.orEmpty())
    }

    suspend fun loadCropPreview(uri: Uri): Bitmap = imageManager.loadCropPreview(uri)

    fun addCroppedPhoto(mealType: MealType, source: Uri, bitmap: Bitmap) {
        val date = uiState.value.date
        viewModelScope.launch {
            imageImportMutex.withLock {
                saving.value = true
                error.value = null
                runCatching {
                    val quality = settingsRepository.settings.first().imageQuality
                    val path = imageManager.importCroppedImage(source, bitmap, date, quality)
                    runCatching {
                        repository.addPhoto(MealPhoto(UUID.randomUUID().toString(), date, mealType, path, System.currentTimeMillis()))
                    }.onFailure { imageManager.delete(path) }.getOrThrow()
                }.onFailure {
                    bitmap.takeUnless(Bitmap::isRecycled)?.recycle()
                    error.value = it.message ?: "添加照片失败"
                }
                saving.value = false
            }
        }
    }

    fun discardPendingImage(uri: Uri) {
        viewModelScope.launch { imageManager.discardPendingImage(uri) }
    }

    fun deletePhoto(photo: MealPhoto) {
        if (saving.value) return
        viewModelScope.launch {
            saving.value = true
            error.value = null
            runCatching {
                check(imageManager.delete(photo.localPath)) { "无法删除本地图片" }
                repository.deletePhotoRecord(photo.id)
            }.onFailure { error.value = it.message ?: "删除照片失败" }
            saving.value = false
        }
    }

    fun saveWeight(isMorning: Boolean, weight: Double?) {
        if (saving.value) return
        val state = uiState.value
        viewModelScope.launch {
            saving.value = true
            error.value = null
            runCatching {
                repository.updateWeights(
                    date = state.date,
                    morning = if (isMorning) weight else state.record?.morningWeight,
                    evening = if (isMorning) state.record?.eveningWeight else weight,
                )
            }.onFailure { error.value = it.message ?: "保存体重失败" }
            saving.value = false
        }
    }
}

class TodayViewModelFactory(
    private val repository: FoodDiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val imageManager: LocalImageManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(repository, settingsRepository, imageManager) as T
}
