package com.johnpaulpatigas.motofuel.engine

import com.johnpaulpatigas.motofuel.data.local.RefuelingEntity
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.data.model.ReliabilityLevel
import com.johnpaulpatigas.motofuel.data.model.TrendPoint
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimation engine that processes refueling records, computes interval distance,
 * cumulative baseline estimators, recent moving averages, RLS online predictions,
 * and multi-factor statistical reliability.
 */
class EstimationEngine(
    private val rlsEngine: RecursiveLeastSquaresEngine = RecursiveLeastSquaresEngine()
) {

    /**
     * Enriches raw database entities with interval distance and per-interval km/L.
     * Records are sorted chronologically by timestamp and odometer reading.
     */
    fun computeDerivedRecords(entities: List<RefuelingEntity>): List<RefuelingRecord> {
        if (entities.isEmpty()) return emptyList()

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
                val rawInterval = current.odometerKm - previous.odometerKm
                val intervalKm = max(0.0, rawInterval)

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
                        intervalKm = intervalKm,
                        intervalKmLiters = intervalKmLiters,
                        isBaseline = false
                    )
                )
            }
        }

        return result
    }

    /**
     * Compute full estimation summary, including cumulative baseline km/L,
     * RLS online predictions, multi-factor statistical confidence, and trend points.
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

        // Cumulative Baseline Estimator: Total Distance / Total Fuel Consumed
        val cumulativeKmLiters = if (totalFuelConsumed > 0.0) totalDistanceKm / totalFuelConsumed else null

        // Recent moving average (last 3 intervals)
        val recentIntervals = intervals.takeLast(3)
        val recentDistanceKm = recentIntervals.sumOf { it.intervalKm ?: 0.0 }
        val recentFuelLiters = recentIntervals.sumOf { it.litersAdded }
        val recentKmLiters = if (recentFuelLiters > 0.0) recentDistanceKm / recentFuelLiters else null

        // Rolling Window Estimators (W = 3, 5, 8, 10)
        val observations = intervals.map { Pair(it.intervalKm ?: 0.0, it.litersAdded) }
        val windowSizes = listOf(3, 5, 8, 10)
        val rollingWindowMap = windowSizes.associateWith { w ->
            RollingWindowEstimator(w).evaluateLatest(observations)
        }
        val rollingWindowKmLiters = rollingWindowMap[5] ?: recentKmLiters

        // Run RLS online model sequentially
        val effectiveRlsEngine = rlsEngine
        val rlsStates = effectiveRlsEngine.processSequence(observations)
        val finalRlsState = rlsStates.lastOrNull() ?: RlsState()

        val predictedKmLiters = if (finalRlsState.sampleCount > 0 && finalRlsState.theta > 0.0) {
            finalRlsState.theta
        } else cumulativeKmLiters

        val uncertaintyMargin = if (finalRlsState.sampleCount >= 2) {
            finalRlsState.marginOfError95
        } else null

        // Run CUSUM sequential change detector on RLS innovation sequence
        val cusumDetector = CusumTrendDetector(kappa = 0.75, thresholdH = 5.0)
        var cusumState = CusumState()
        val cusumStates = mutableListOf<CusumState>()

        for (k in observations.indices) {
            val (dist, fuel) = observations[k]
            val priorRlsState = if (k > 0) rlsStates[k - 1] else RlsState()

            val priorTheta = if (k > 0) {
                rlsStates[k - 1].theta
            } else if (effectiveRlsEngine.initialThetaPrior > 0.0) {
                effectiveRlsEngine.initialThetaPrior
            } else {
                dist / fuel
            }

            val priorP = priorRlsState.covariance
            val priorNeff = priorRlsState.effectiveSampleSize
            val priorRss = priorRlsState.residualSumOfSquares

            val priorVarEst = if (priorNeff > 1.0) priorRss / (priorNeff - 1.0) else 100.0
            val innovationVar = max(1e-4, priorVarEst * (1.0 + fuel * fuel * priorP))
            val innovationError = dist - (priorTheta * fuel)

            cusumState = cusumDetector.update(cusumState, innovationError, innovationVar)
            cusumStates.add(cusumState)
        }
        val finalCusumState = cusumStates.lastOrNull() ?: CusumState()

        // Vehicle Setup & Cost Metrics
        // Authoritative long-term cumulative estimator drives theoretical full-tank range
        val estimatedRangeKm = if (tankCapacityLiters > 0.0 && cumulativeKmLiters != null && cumulativeKmLiters > 0.0) {
            cumulativeKmLiters * tankCapacityLiters
        } else null

        val distanceSinceLastRefuelKm = intervals.lastOrNull()?.intervalKm
        val efficiencyVsTarget = if (targetKmLiters > 0.0 && cumulativeKmLiters != null) {
            cumulativeKmLiters - targetKmLiters
        } else null

        val costPerKm = if (totalDistanceKm > 0.0 && totalSpendCost > 0.0) {
            totalSpendCost / totalDistanceKm
        } else null

        // Extract recent predictions for stability evaluation
        val recentPredictions = rlsStates.takeLast(5).map { it.theta }.filter { it > 0.0 }

        // Determine multi-factor statistical reliability level
        val reliability = determineReliabilityLevel(
            intervalCount = intervals.size,
            totalDistanceKm = totalDistanceKm,
            predictedKmLiters = predictedKmLiters,
            standardError = finalRlsState.standardError,
            marginOfError = uncertaintyMargin,
            recentPredictions = recentPredictions
        )

        val explanation = buildExplanation(
            reliability = reliability,
            intervalCount = intervals.size,
            totalDistanceKm = totalDistanceKm,
            uncertaintyMargin = uncertaintyMargin
        )

        // Build trend points for UI charts
        var runningDistance = 0.0
        var runningFuel = 0.0
        val trendPoints = mutableListOf<TrendPoint>()
        val rolling5Sequence = RollingWindowEstimator(5).processSequence(observations)

        for (idx in intervals.indices) {
            val record = intervals[idx]
            val rlsStateAtIdx = rlsStates.getOrNull(idx)
            val dist = record.intervalKm ?: 0.0
            val fuel = record.litersAdded

            runningDistance += dist
            runningFuel += fuel
            val cumulativeAtIdx = if (runningFuel > 0.0) runningDistance / runningFuel else null
            val rollingAtIdx = rolling5Sequence.getOrNull(idx)

            trendPoints.add(
                TrendPoint(
                    recordId = record.id,
                    index = idx + 1,
                    odometerKm = record.odometerKm,
                    intervalKmLiters = record.intervalKmLiters,
                    rlsPredictedKmLiters = rlsStateAtIdx?.theta,
                    cumulativeKmLiters = cumulativeAtIdx,
                    rollingWindowKmLiters = rollingAtIdx
                )
            )
        }

        return EstimationResult(
            reliability = reliability,
            cumulativeKmLiters = cumulativeKmLiters,
            recentKmLiters = recentKmLiters,
            rollingWindowKmLiters = rollingWindowKmLiters,
            rollingWindowMap = rollingWindowMap,
            predictedKmLiters = predictedKmLiters,
            uncertaintyMargin = uncertaintyMargin,
            efficiencyChangeSignal = finalCusumState.signal,
            cusumGPlus = finalCusumState.gPlus,
            cusumGMinus = finalCusumState.gMinus,
            totalDistanceKm = totalDistanceKm,
            totalFuelConsumedLiters = totalFuelConsumed,
            totalFuelAddedLiters = totalFuelAdded,
            recordCount = recordCount,
            intervalCount = intervals.size,
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

    private fun determineReliabilityLevel(
        intervalCount: Int,
        totalDistanceKm: Double,
        predictedKmLiters: Double?,
        standardError: Double?,
        marginOfError: Double?,
        recentPredictions: List<Double>
    ): ReliabilityLevel {
        if (intervalCount < 1 || totalDistanceKm < 20.0 || predictedKmLiters == null || predictedKmLiters <= 0.0) {
            return ReliabilityLevel.INSUFFICIENT_DATA
        }

        val relativeSe = if (standardError != null && standardError > 0.0) standardError / predictedKmLiters else 1.0
        val relativeMoe = if (marginOfError != null && marginOfError > 0.0) marginOfError / predictedKmLiters else 1.0

        val recentCoeffVar = if (recentPredictions.size >= 3) {
            val mean = recentPredictions.average()
            if (mean > 0.0) {
                val variance = recentPredictions.sumOf { (it - mean) * (it - mean) } / recentPredictions.size
                sqrt(variance) / mean
            } else 1.0
        } else 1.0

        return when {
            intervalCount >= 5 && totalDistanceKm >= 300.0 && relativeMoe <= 0.25 && relativeSe <= 0.10 && recentCoeffVar <= 0.08 -> {
                ReliabilityLevel.HIGH
            }
            intervalCount >= 2 && totalDistanceKm >= 100.0 && relativeSe <= 0.25 -> {
                ReliabilityLevel.MEDIUM
            }
            else -> {
                ReliabilityLevel.LOW
            }
        }
    }

    private fun buildExplanation(
        reliability: ReliabilityLevel,
        intervalCount: Int,
        totalDistanceKm: Double,
        uncertaintyMargin: Double?
    ): String {
        val distStr = formatKm(totalDistanceKm)
        val marginStr = uncertaintyMargin?.let { " (±${formatOneDecimal(it)} km/L)" } ?: ""

        return when (reliability) {
            ReliabilityLevel.INSUFFICIENT_DATA -> {
                "At least 2 refueling records (1 distance interval) with increased odometer readings are required to begin estimating fuel economy."
            }
            ReliabilityLevel.LOW -> {
                "Low statistical confidence based on $intervalCount interval ($distStr km recorded). Add more refueling logs to reduce partial-tank inventory noise."
            }
            ReliabilityLevel.MEDIUM -> {
                "Medium confidence based on $intervalCount intervals across $distStr km$marginStr."
            }
            ReliabilityLevel.HIGH -> {
                "High confidence based on $intervalCount intervals across $distStr km$marginStr."
            }
        }
    }

    private fun formatKm(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
    private fun formatOneDecimal(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
}
