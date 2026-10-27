package com.wkzxmwk.fooddiary.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.data.local.entity.MealPhotoEntity

data class DailyRecordWithPhotos(
    @Embedded val record: DailyRecordEntity,
    @Relation(parentColumn = "dateEpochDay", entityColumn = "recordDateEpochDay")
    val photos: List<MealPhotoEntity>,
)

