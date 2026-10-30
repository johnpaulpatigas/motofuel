package com.johnpaulpatigas.motofuel.data.model

enum class ReliabilityLevel {
    INSUFFICIENT_DATA,
    LOW,
    MEDIUM,
    HIGH
}

data class TrendPoint(
    val recordId: Long,
    val index: Int,
    val odometerKm: Double,
    val intervalKmLiters: Double?,
    val rlsPredictedKmLiters: Double?,
    val cumulativeKmLiters: Double?
)

data class EstimationResult(
    val reliability: ReliabilityLevel = ReliabilityLevel.INSUFFICIENT_DATA,
    val cumulativeKmLiters: Double? = null,
    val recentKmLiters: Double? = null,
    val predictedKmLiters: Double? = null,
    val uncertaintyMargin: Double? = null,
    val totalDistanceKm: Double = 0.0,
    val totalFuelConsumedLiters: Double = 0.0,
    val totalFuelAddedLiters: Double = 0.0,
    val recordCount: Int = 0,
    val intervalCount: Int = 0,
    val explanation: String = "",
    val trendPoints: List<TrendPoint> = emptyList(),
    val tankCapacityLiters: Double = 0.0,
    val estimatedRangeKm: Double? = null,
    val distanceSinceLastRefuelKm: Double? = null,
    val targetKmLiters: Double = 0.0,
    val efficiencyVsTarget: Double? = null,
    val totalSpendCost: Double = 0.0,
    val costPerKm: Double? = null,
    val currencySymbol: String = "₱"
)
