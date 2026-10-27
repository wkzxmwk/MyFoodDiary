package com.wkzxmwk.fooddiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightCalculatorTest {
    @Test fun `早晚体重都存在时计算一位小数平均值`() {
        assertEquals(68.1, WeightCalculator.average(68.25, 67.95)!!, 0.0)
    }

    @Test fun `缺少任意体重时没有平均值`() {
        assertNull(WeightCalculator.average(68.2, null))
        assertNull(WeightCalculator.average(null, 67.8))
    }

    @Test fun `边界体重有效`() {
        assertTrue(WeightCalculator.isValid(20.0))
        assertTrue(WeightCalculator.isValid(300.0))
    }
}

