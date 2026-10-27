package com.wkzxmwk.fooddiary.domain.model

import java.time.LocalTime

enum class ReminderType(
    val displayName: String,
    val defaultTime: LocalTime,
    val notificationText: String,
) {
    BREAKFAST("早餐提醒", LocalTime.of(8, 0), "记得记录早餐和早晨体重"),
    LUNCH("午餐提醒", LocalTime.of(12, 0), "记得记录今天的午餐"),
    DINNER("晚餐提醒", LocalTime.of(19, 0), "记得记录晚餐和晚上体重"),
}
