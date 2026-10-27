package com.wkzxmwk.fooddiary.domain

import kotlin.math.round

object BodyMetricsCalculator {
    const val MIN_HEIGHT_CM = 50.0
    const val MAX_HEIGHT_CM = 250.0

    fun isValidHeight(heightCm: Double): Boolean =
        heightCm.isFinite() && heightCm in MIN_HEIGHT_CM..MAX_HEIGHT_CM

    fun normalizeHeight(heightCm: Double): Double = round(heightCm * 10.0) / 10.0

    fun bmi(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || heightCm == null) return null
        if (!WeightCalculator.isValid(weightKg) || !isValidHeight(heightCm)) return null
        val heightMeters = heightCm / 100.0
        return round(weightKg / (heightMeters * heightMeters) * 10.0) / 10.0
    }
}
