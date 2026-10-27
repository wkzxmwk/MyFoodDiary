package com.wkzxmwk.fooddiary.data

import com.wkzxmwk.fooddiary.data.local.dao.DailyRecordDao
import com.wkzxmwk.fooddiary.data.local.dao.MealPhotoDao
import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.data.local.entity.MealPhotoEntity
import com.wkzxmwk.fooddiary.data.local.relation.DailyRecordWithPhotos
import com.wkzxmwk.fooddiary.data.repository.FoodDiaryRepositoryImpl
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDiaryRepositoryImplTest {
    @Test fun `更新体重会创建记录并规范到两位小数`() = runTest {
        val dailyDao = FakeDailyRecordDao()
        val repository = FoodDiaryRepositoryImpl(dailyDao, FakeMealPhotoDao(), now = { 100L })
        val date = LocalDate.of(2026, 7, 22)

        repository.updateWeights(date, 68.236, 67.804)

        val record = repository.observeRecord(date).first()
        assertEquals(68.24, record!!.morningWeight!!, 0.0)
        assertEquals(67.8, record.eveningWeight!!, 0.0)
        assertEquals(68.0, record.averageWeight!!, 0.0)
    }

    @Test fun `拒绝超出范围的体重`() = runTest {
        val repository = FoodDiaryRepositoryImpl(FakeDailyRecordDao(), FakeMealPhotoDao())
        var thrown = false
        try {
            repository.updateWeights(LocalDate.now(), 19.9, null)
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}

private class FakeDailyRecordDao : DailyRecordDao {
    private val records = MutableStateFlow<Map<Long, DailyRecordEntity>>(emptyMap())

    override suspend fun upsert(record: DailyRecordEntity) {
        records.value = records.value + (record.dateEpochDay to record)
    }
    override suspend fun upsertAll(records: List<DailyRecordEntity>) { records.forEach { upsert(it) } }

    override suspend fun get(dateEpochDay: Long) = records.value[dateEpochDay]

    override fun observeWithPhotos(dateEpochDay: Long): Flow<DailyRecordWithPhotos?> =
        records.map { values -> values[dateEpochDay]?.let { DailyRecordWithPhotos(it, emptyList()) } }

    override fun observeRange(start: Long, end: Long): Flow<List<DailyRecordWithPhotos>> = records.map { values ->
        values.values.filter { it.dateEpochDay in start..end }.sortedByDescending { it.dateEpochDay }
            .map { DailyRecordWithPhotos(it, emptyList()) }
    }

    override fun observeAll(): Flow<List<DailyRecordWithPhotos>> = records.map { values ->
        values.values.sortedBy { it.dateEpochDay }.map { DailyRecordWithPhotos(it, emptyList()) }
    }

    override suspend fun delete(dateEpochDay: Long) {
        records.value = records.value - dateEpochDay
    }

    override suspend fun deleteAll() {
        records.value = emptyMap()
    }
}

private class FakeMealPhotoDao : MealPhotoDao {
    private val photos = mutableMapOf<String, MealPhotoEntity>()
    override suspend fun upsert(photo: MealPhotoEntity) { photos[photo.id] = photo }
    override suspend fun upsertAll(photos: List<MealPhotoEntity>) { photos.forEach { upsert(it) } }
    override suspend fun get(id: String) = photos[id]
    override suspend fun getForDate(dateEpochDay: Long) = photos.values.filter { it.recordDateEpochDay == dateEpochDay }
    override suspend fun getAll() = photos.values.toList()
    override suspend fun delete(id: String) { photos.remove(id) }
}
