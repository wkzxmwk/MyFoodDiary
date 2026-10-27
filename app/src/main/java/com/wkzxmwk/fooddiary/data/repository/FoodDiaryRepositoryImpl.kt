package com.wkzxmwk.fooddiary.data.repository

import com.wkzxmwk.fooddiary.data.local.dao.DailyRecordDao
import com.wkzxmwk.fooddiary.data.local.dao.MealPhotoDao
import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.domain.WeightCalculator
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FoodDiaryRepositoryImpl(
    private val dailyRecordDao: DailyRecordDao,
    private val mealPhotoDao: MealPhotoDao,
    private val now: () -> Long = System::currentTimeMillis,
) : FoodDiaryRepository {
    override fun observeRecord(date: LocalDate): Flow<DailyRecord?> =
        dailyRecordDao.observeWithPhotos(date.toEpochDay()).map { it?.toDomain() }

    override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyRecord>> =
        dailyRecordDao.observeRange(start.toEpochDay(), end.toEpochDay()).map { records -> records.map { it.toDomain() } }

    override fun observeAll(): Flow<List<DailyRecord>> =
        dailyRecordDao.observeAll().map { records -> records.map { it.toDomain() } }

    override suspend fun updateWeights(date: LocalDate, morning: Double?, evening: Double?) {
        require(morning == null || WeightCalculator.isValid(morning)) { "早晨体重应在20至300kg之间" }
        require(evening == null || WeightCalculator.isValid(evening)) { "晚上体重应在20至300kg之间" }
        updateRecord(date) { current, timestamp ->
            current.copy(
                morningWeight = morning?.let(WeightCalculator::normalize),
                eveningWeight = evening?.let(WeightCalculator::normalize),
                updatedAt = timestamp,
            )
        }
    }

    override suspend fun updateNote(date: LocalDate, note: String) {
        updateRecord(date) { current, timestamp -> current.copy(note = note.trim(), updatedAt = timestamp) }
    }

    override suspend fun addPhoto(photo: MealPhoto) {
        ensureRecord(photo.recordDate)
        mealPhotoDao.upsert(photo.toEntity())
    }

    override suspend fun getPhoto(id: String): MealPhoto? = mealPhotoDao.get(id)?.toDomain()
    override suspend fun getPhotos(date: LocalDate): List<MealPhoto> = mealPhotoDao.getForDate(date.toEpochDay()).map { it.toDomain() }
    override suspend fun deletePhotoRecord(id: String) = mealPhotoDao.delete(id)
    override suspend fun deleteRecord(date: LocalDate) = dailyRecordDao.delete(date.toEpochDay())
    override suspend fun deleteAll() = dailyRecordDao.deleteAll()

    private suspend fun ensureRecord(date: LocalDate): DailyRecordEntity {
        dailyRecordDao.get(date.toEpochDay())?.let { return it }
        val timestamp = now()
        return DailyRecordEntity(date.toEpochDay(), null, null, "", timestamp, timestamp)
            .also { dailyRecordDao.upsert(it) }
    }

    private suspend fun updateRecord(
        date: LocalDate,
        transform: (DailyRecordEntity, Long) -> DailyRecordEntity,
    ) {
        val current = ensureRecord(date)
        dailyRecordDao.upsert(transform(current, now()))
    }
}

