package com.wkzxmwk.fooddiary.domain

import java.math.RoundingMode

object WeightCalculator {
    const val MIN_WEIGHT = 20.0
    const val MAX_WEIGHT = 300.0

    fun isValid(weight: Double): Boolean = weight in MIN_WEIGHT..MAX_WEIGHT

    fun normalize(weight: Double): Double =
        weight.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()

    fun average(morningWeight: Double?, eveningWeight: Double?): Double? {
        if (morningWeight == null || eveningWeight == null) return null
        return ((morningWeight + eveningWeight) / 2.0)
            .toBigDecimal()
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
    }
}

