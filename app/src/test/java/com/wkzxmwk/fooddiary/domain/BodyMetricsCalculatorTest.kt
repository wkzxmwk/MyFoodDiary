package com.wkzxmwk.fooddiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMetricsCalculatorTest {
    @Test fun `BMI按体重和身高计算并保留一位小数`() {
        assertEquals(22.9, BodyMetricsCalculator.bmi(70.0, 175.0)!!, 0.0)
    }

    @Test fun `缺少体重或身高时没有BMI`() {
        assertNull(BodyMetricsCalculator.bmi(null, 175.0))
        assertNull(BodyMetricsCalculator.bmi(70.0, null))
    }

    @Test fun `校验身高合理范围`() {
        assertTrue(BodyMetricsCalculator.isValidHeight(50.0))
        assertTrue(BodyMetricsCalculator.isValidHeight(250.0))
        assertFalse(BodyMetricsCalculator.isValidHeight(49.9))
        assertFalse(BodyMetricsCalculator.isValidHeight(250.1))
    }
}
