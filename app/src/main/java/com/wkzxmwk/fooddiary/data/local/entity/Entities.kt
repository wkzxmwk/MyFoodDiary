package com.wkzxmwk.fooddiary.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wkzxmwk.fooddiary.domain.model.MealType

@Entity(tableName = "daily_records")
data class DailyRecordEntity(
    @PrimaryKey val dateEpochDay: Long,
    val morningWeight: Double?,
    val eveningWeight: Double?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "meal_photos",
    foreignKeys = [
        ForeignKey(
            entity = DailyRecordEntity::class,
            parentColumns = ["dateEpochDay"],
            childColumns = ["recordDateEpochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("recordDateEpochDay"),
        Index(value = ["recordDateEpochDay", "mealType", "createdAt"]),
    ],
)
data class MealPhotoEntity(
    @PrimaryKey val id: String,
    val recordDateEpochDay: Long,
    val mealType: MealType,
    val localPath: String,
    val createdAt: Long,
)

