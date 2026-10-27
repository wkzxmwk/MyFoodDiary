package com.wkzxmwk.fooddiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wkzxmwk.fooddiary.data.local.entity.MealPhotoEntity

@Dao
interface MealPhotoDao {
    @Upsert
    suspend fun upsert(photo: MealPhotoEntity)

    @Upsert
    suspend fun upsertAll(photos: List<MealPhotoEntity>)

    @Query("SELECT * FROM meal_photos WHERE id = :id")
    suspend fun get(id: String): MealPhotoEntity?

    @Query("SELECT * FROM meal_photos WHERE recordDateEpochDay = :dateEpochDay")
    suspend fun getForDate(dateEpochDay: Long): List<MealPhotoEntity>

    @Query("SELECT * FROM meal_photos")
    suspend fun getAll(): List<MealPhotoEntity>

    @Query("DELETE FROM meal_photos WHERE id = :id")
    suspend fun delete(id: String)
}
