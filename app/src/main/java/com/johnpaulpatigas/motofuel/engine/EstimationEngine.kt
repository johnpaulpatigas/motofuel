package com.johnpaulpatigas.motofuel.engine

import com.johnpaulpatigas.motofuel.data.local.RefuelingEntity
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.data.model.ReliabilityLevel
import com.johnpaulpatigas.motofuel.data.model.TrendPoint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Estimation engine that processes refueling records, computes interval distance,
 * baseline behavior, cumulative averages, recent moving averages, RLS online predictions,
 * and statistical uncertainty.
 */
class EstimationEngine(
    private val rlsEngine: RecursiveLeastSquaresEngine = RecursiveLeastSquaresEngine()
) {

    /**
     * Enriches raw database entities with interval distance and per-interval km/L.
     * Records are assumed to be sorted chronologically by odometer reading.
     */
    fun computeDerivedRecords(entities: List<RefuelingEntity>): List<RefuelingRecord> {
        if (entities.isEmpty()) return emptyList()

        // Sort chronologically by timestamp, then odometer
        val sorted = entities.sortedWith(
            compareBy<RefuelingEntity> { it.timestamp }.thenBy { it.odometerKm }
        )

        val result = mutableListOf<RefuelingRecord>()

        for (i in sorted.indices) {
            val current = sorted[i]
            if (i == 0) {
                // First record is baseline
                result.add(
                    RefuelingRecord(
                        id = current.id,
                        odometerKm = current.odometerKm,
                        litersAdded = current.litersAdded,
                        pricePerLiter = current.pricePerLiter,
                        timestamp = current.timestamp,
                        intervalKm = null,
                        intervalKmLiters = null,
                        isBaseline = true
                    )
                )
            } else {
                val previous = sorted[i - 1]
                val intervalKm = current.odometerKm - previous.odometerKm

                val intervalKmLiters = if (intervalKm > 0.0 && current.litersAdded > 0.0) {
                    intervalKm / current.litersAdded
                } else null

                result.add(
                    RefuelingRecord(
                        id = current.id,
                        odometerKm = current.odometerKm,
                        litersAdded = current.litersAdded,
                        pricePerLiter = current.pricePerLiter,
                        timestamp = current.timestamp,
                        intervalKm = max(0.0, intervalKm),
                        intervalKmLiters = intervalKmLiters,
                        isBaseline = false
                    )
                )
            }
        }

        return result
    }

    /**
     * Compute full estimation summary, including cumulative km/L, recent km/L,
     * RLS prediction, statistical confidence, and trend points.
     */
    fun evaluate(
        records: List<RefuelingRecord>,
        tankCapacityLiters: Double = 0.0,
        targetKmLiters: Double = 0.0,
        currencySymbol: String = "₱"
    ): EstimationResult {
        if (records.isEmpty()) {
            return EstimationResult(
                reliability = ReliabilityLevel.INSUFFICIENT_DATA,
                explanation = "No refueling records stored. Add your first record to set the odometer baseline.",
                tankCapacityLiters = tankCapacityLiters,
                targetKmLiters = targetKmLiters,
                currencySymbol = currencySymbol
            )
        }

        val totalFuelAdded = records.sumOf { it.litersAdded }
        val recordCount = records.size
        val totalSpendCost = records.sumOf { (it.pricePerLiter ?: 0.0) * it.litersAdded }

        if (records.size == 1) {
            val baseline = records.first()
            return EstimationResult(
                reliability = ReliabilityLevel.INSUFFICIENT_DATA,
                totalDistanceKm = 0.0,
                totalFuelConsumedLiters = 0.0,
                totalFuelAddedLiters = baseline.litersAdded,
                recordCount = 1,
                intervalCount = 0,
                totalSpendCost = totalSpendCost,
                tankCapacityLiters = tankCapacityLiters,
                targetKmLiters = targetKmLiters,
                currencySymbol = currencySymbol,
                explanation = "Baseline odometer established at ${formatKm(baseline.odometerKm)} km. Add a second record after riding to calculate fuel economy."
            )
        }

        // Filter valid interval observations
        val intervals = records.drop(1).filter {
            (it.intervalKm ?: 0.0) > 0.0 && it.litersAdded > 0.0
        }

        if (intervals.isEmpty()) {
            return EstimationResult(
                reliability = ReliabilityLevel.INSUFFICIENT_DATA,
                totalFuelAddedLiters = totalFuelAdded,
                recordCount = recordCount,
                intervalCount = 0,
                totalSpendCost = totalSpendCost,
                tankCapacityLiters = tankCapacityLiters,
                targetKmLiters = targetKmLiters,
                currencySymbol = currencySymbol,
                explanation = "Insufficient valid distance recorded between refuels. Ensure odometer readings increase."
            )
        }

        val totalDistanceKm = intervals.sumOf { it.intervalKm ?: 0.0 }
        val totalFuelConsumed = intervals.sumOf { it.litersAdded }
        val cumulativeKmLiters = if (totalFuelConsumed > 0.0) totalDistanceKm / totalFuelConsumed else null

        // Recent moving average (last 3 intervals)
        val recentIntervals = intervals.takeLast(3)
        val recentDistanceKm = recentIntervals.sumOf { it.intervalKm ?: 0.0 }
        val recentFuelLiters = recentIntervals.sumOf { it.litersAdded }
        val recentKmLiters = if (recentFuelLiters > 0.0) recentDistanceKm / recentFuelLiters else null

        // Run RLS online model sequentially
        val observations = intervals.map { Pair(it.intervalKm ?: 0.0, it.litersAdded) }
        val rlsStates = rlsEngine.processSequence(observations)
        val finalRlsState = rlsStates.lastOrNull() ?: RlsState()

        val predictedKmLiters = if (finalRlsState.sampleCount > 0 && finalRlsState.theta > 0.0) {
            finalRlsState.theta
        } else cumulativeKmLiters

        val uncertaintyMargin = if (finalRlsState.sampleCount >= 2) {
            finalRlsState.marginOfError95
        } else null

        // Vehicle Setup & Cost Metrics
        val estimatedRangeKm = if (tankCapacityLiters > 0.0 && predictedKmLiters != null) {
            predictedKmLiters * tankCapacityLiters
        } else null

        val distanceSinceLastRefuelKm = intervals.lastOrNull()?.intervalKm
        val efficiencyVsTarget = if (targetKmLiters > 0.0 && predictedKmLiters != null) {
            predictedKmLiters - targetKmLiters
        } else null

        val costPerKm = if (totalDistanceKm > 0.0 && totalSpendCost > 0.0) {
            totalSpendCost / totalDistanceKm
        } else null

        // Determine reliability rating
        val intervalCount = intervals.size
        val reliability = when {
            intervalCount < 2 -> ReliabilityLevel.LOW
            intervalCount in 2..4 -> ReliabilityLevel.MEDIUM
            else -> ReliabilityLevel.HIGH
        }

        val explanation = buildExplanation(
            reliability = reliability,
            intervalCount = intervalCount,
            uncertaintyMargin = uncertaintyMargin,
            cumulativeKmLiters = cumulativeKmLiters,
            predictedKmLiters = predictedKmLiters
        )

        // Build trend points for chart
        var runningDistance = 0.0
        var runningFuel = 0.0
        val trendPoints = mutableListOf<TrendPoint>()

        for (idx in intervals.indices) {
            val record = intervals[idx]
            val rlsStateAtIdx = rlsStates.getOrNull(idx)
            val dist = record.intervalKm ?: 0.0
            val fuel = record.litersAdded

            runningDistance += dist
            runningFuel += fuel
            val cumulativeAtIdx = if (runningFuel > 0.0) runningDistance / runningFuel else null

            trendPoints.add(
                TrendPoint(
                    recordId = record.id,
                    index = idx + 1,
                    odometerKm = record.odometerKm,
                    intervalKmLiters = record.intervalKmLiters,
                    rlsPredictedKmLiters = rlsStateAtIdx?.theta,
                    cumulativeKmLiters = cumulativeAtIdx
                )
            )
        }

        return EstimationResult(
            reliability = reliability,
            cumulativeKmLiters = cumulativeKmLiters,
            recentKmLiters = recentKmLiters,
            predictedKmLiters = predictedKmLiters,
            uncertaintyMargin = uncertaintyMargin,
            totalDistanceKm = totalDistanceKm,
            totalFuelConsumedLiters = totalFuelConsumed,
            totalFuelAddedLiters = totalFuelAdded,
            recordCount = recordCount,
            intervalCount = intervalCount,
            explanation = explanation,
            trendPoints = trendPoints,
            tankCapacityLiters = tankCapacityLiters,
            estimatedRangeKm = estimatedRangeKm,
            distanceSinceLastRefuelKm = distanceSinceLastRefuelKm,
            targetKmLiters = targetKmLiters,
            efficiencyVsTarget = efficiencyVsTarget,
            totalSpendCost = totalSpendCost,
            costPerKm = costPerKm,
            currencySymbol = currencySymbol
        )
    }

    private fun buildExplanation(
        reliability: ReliabilityLevel,
        intervalCount: Int,
        uncertaintyMargin: Double?,
        cumulativeKmLiters: Double?,
        predictedKmLiters: Double?
    ): String {
        return when (reliability) {
            ReliabilityLevel.INSUFFICIENT_DATA -> {
                "At least 2 refueling records (1 distance interval) are required to begin estimating fuel economy."
            }
            ReliabilityLevel.LOW -> {
                "Low statistical confidence based on $intervalCount interval. Additional refueling logs will improve estimation accuracy."
            }
            ReliabilityLevel.MEDIUM -> {
                val marginStr = uncertaintyMargin?.let { " (±${formatOneDecimal(it)} km/L)" } ?: ""
                "Medium confidence based on $intervalCount intervals$marginStr. Recursive Least Squares algorithm filter active."
            }
            ReliabilityLevel.HIGH -> {
                val marginStr = uncertaintyMargin?.let { " (±${formatOneDecimal(it)} km/L)" } ?: ""
                "High confidence based on $intervalCount intervals$marginStr. Recursive Least Squares algorithm filter active."
            }
        }
    }

    private fun formatKm(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
    private fun formatOneDecimal(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
}
