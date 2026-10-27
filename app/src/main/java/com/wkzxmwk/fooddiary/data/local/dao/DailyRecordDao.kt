package com.wkzxmwk.fooddiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.data.local.relation.DailyRecordWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRecordDao {
    @Upsert
    suspend fun upsert(record: DailyRecordEntity)

    @Upsert
    suspend fun upsertAll(records: List<DailyRecordEntity>)

    @Query("SELECT * FROM daily_records WHERE dateEpochDay = :dateEpochDay")
    suspend fun get(dateEpochDay: Long): DailyRecordEntity?

    @Transaction
    @Query("SELECT * FROM daily_records WHERE dateEpochDay = :dateEpochDay")
    fun observeWithPhotos(dateEpochDay: Long): Flow<DailyRecordWithPhotos?>

    @Transaction
    @Query("SELECT * FROM daily_records WHERE dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay DESC")
    fun observeRange(start: Long, end: Long): Flow<List<DailyRecordWithPhotos>>

    @Transaction
    @Query("SELECT * FROM daily_records ORDER BY dateEpochDay ASC")
    fun observeAll(): Flow<List<DailyRecordWithPhotos>>

    @Query("DELETE FROM daily_records WHERE dateEpochDay = :dateEpochDay")
    suspend fun delete(dateEpochDay: Long)

    @Query("DELETE FROM daily_records")
    suspend fun deleteAll()
}
