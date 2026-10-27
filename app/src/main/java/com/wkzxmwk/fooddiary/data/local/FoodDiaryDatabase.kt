package com.wkzxmwk.fooddiary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.wkzxmwk.fooddiary.data.local.dao.DailyRecordDao
import com.wkzxmwk.fooddiary.data.local.dao.MealPhotoDao
import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.data.local.entity.MealPhotoEntity
import com.wkzxmwk.fooddiary.domain.model.MealType

class RoomConverters {
    @TypeConverter fun mealTypeToString(value: MealType): String = value.name
    @TypeConverter fun stringToMealType(value: String): MealType = MealType.valueOf(value)
}

@Database(
    entities = [DailyRecordEntity::class, MealPhotoEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class FoodDiaryDatabase : RoomDatabase() {
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun mealPhotoDao(): MealPhotoDao
}

