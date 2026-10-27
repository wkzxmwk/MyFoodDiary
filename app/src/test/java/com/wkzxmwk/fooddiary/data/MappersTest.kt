package com.wkzxmwk.fooddiary.data

import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.data.local.relation.DailyRecordWithPhotos
import com.wkzxmwk.fooddiary.data.repository.toDomain
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test fun `Room日期转换为LocalDate`() {
        val date = LocalDate.of(2026, 7, 22)
        val relation = DailyRecordWithPhotos(
            DailyRecordEntity(date.toEpochDay(), 68.2, 67.8, "备注", 1, 2),
            emptyList(),
        )
        val domain = relation.toDomain()
        assertEquals(date, domain.date)
        assertEquals(68.0, domain.averageWeight!!, 0.0)
    }
}
