package com.johnpaulpatigas.motofuel.data.model

import com.johnpaulpatigas.motofuel.engine.EfficiencyChangeType

enum class ReliabilityLevel {
    INSUFFICIENT_DATA,
    LOW,
    MEDIUM,
    HIGH
}

data class TrendPoint(
    val odometerKm: Double,
    val rlsPredictedKmLiters: Double?
)

data class EstimationResult(
    val reliability: ReliabilityLevel = ReliabilityLevel.INSUFFICIENT_DATA,
    val predictedKmLiters: Double? = null,
    val uncertaintyMargin: Double? = null,
    val efficiencyChangeSignal: EfficiencyChangeType = EfficiencyChangeType.NONE,
    val totalDistanceKm: Double = 0.0,
    val totalFuelConsumedLiters: Double = 0.0,
    val totalFuelAddedLiters: Double = 0.0,
    val recordCount: Int = 0,
    val intervalCount: Int = 0,
    val trendPoints: List<TrendPoint> = emptyList(),
    val tankCapacityLiters: Double = 0.0,
    val estimatedRangeKm: Double? = null,
    val targetKmLiters: Double = 0.0,
    val efficiencyVsTarget: Double? = null,
    val totalSpendCost: Double = 0.0,
    val costPerKm: Double? = null,
    val currencySymbol: String = "₱"
)
