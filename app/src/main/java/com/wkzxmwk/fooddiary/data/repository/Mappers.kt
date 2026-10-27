package com.wkzxmwk.fooddiary.data.repository

import com.wkzxmwk.fooddiary.data.local.entity.MealPhotoEntity
import com.wkzxmwk.fooddiary.data.local.relation.DailyRecordWithPhotos
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import java.time.LocalDate

fun MealPhotoEntity.toDomain() = MealPhoto(
    id = id,
    recordDate = LocalDate.ofEpochDay(recordDateEpochDay),
    mealType = mealType,
    localPath = localPath,
    createdAt = createdAt,
)

fun MealPhoto.toEntity() = MealPhotoEntity(
    id = id,
    recordDateEpochDay = recordDate.toEpochDay(),
    mealType = mealType,
    localPath = localPath,
    createdAt = createdAt,
)

fun DailyRecordWithPhotos.toDomain() = DailyRecord(
    date = LocalDate.ofEpochDay(record.dateEpochDay),
    morningWeight = record.morningWeight,
    eveningWeight = record.eveningWeight,
    note = record.note,
    photos = photos.sortedBy { it.createdAt }.map(MealPhotoEntity::toDomain),
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
)

