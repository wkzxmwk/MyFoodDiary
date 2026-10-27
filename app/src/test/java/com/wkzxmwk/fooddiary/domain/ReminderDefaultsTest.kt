package com.wkzxmwk.fooddiary.domain

import com.wkzxmwk.fooddiary.domain.model.AppSettings
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDefaultsTest {
    @Test fun `提醒默认开启并使用早中晚默认时间`() {
        val settings = AppSettings()

        assertTrue(settings.remindersEnabled)
        assertEquals(LocalTime.of(8, 0), settings.reminderTime(ReminderType.BREAKFAST))
        assertEquals(LocalTime.of(12, 0), settings.reminderTime(ReminderType.LUNCH))
        assertEquals(LocalTime.of(19, 0), settings.reminderTime(ReminderType.DINNER))
    }
}
