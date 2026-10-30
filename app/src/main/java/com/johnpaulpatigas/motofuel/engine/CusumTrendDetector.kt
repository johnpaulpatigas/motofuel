package com.johnpaulpatigas.motofuel.engine

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Direction of detected efficiency change signal from CUSUM detector.
 */
enum class EfficiencyChangeType {
    NONE,
    EFFICIENCY_DROP,
    EFFICIENCY_IMPROVEMENT
}

/**
 * State container for two-sided CUSUM change detector.
 *
 * @property gPlus Accumulator for negative standardized residuals (efficiency drop / distance deficit).
 * @property gMinus Accumulator for positive standardized residuals (efficiency improvement / distance surplus).
 * @property lastStandardizedResidual Standardized residual z_k = e_k / sqrt(Var(e_k)).
 * @property signal Change signal triggered when accumulator exceeds threshold h.
 */
data class CusumState(
    val gPlus: Double = 0.0,
    val gMinus: Double = 0.0,
    val lastStandardizedResidual: Double = 0.0,
    val signal: EfficiencyChangeType = EfficiencyChangeType.NONE
)

/**
 * Two-sided Cumulative Sum (CUSUM) sequential change detector for monitoring fuel economy trend changes.
 *
 * Uses standardized innovation residuals z_k = e_k / sqrt(Var(e_k)).
 * Accumulates drift:
 * G_k^+ = max(0, G_{k-1}^+ + (-z_k) - kappa)  [Efficiency drop]
 * G_k^- = max(0, G_{k-1}^- + z_k - kappa)     [Efficiency improvement]
 *
 * @param kappa Slack / allowance parameter controlling sensitivity (default 0.75 standard deviations).
 * @param thresholdH Decision threshold h triggering change signal (default 5.0).
 */
class CusumTrendDetector(
    val kappa: Double = 0.75,
    val thresholdH: Double = 5.0
) {
    /**
     * Update CUSUM state given innovation error e_k = D_k - \hat{D}_k and innovation variance Var(e_k).
     */
    fun update(
        prevState: CusumState,
        innovationError: Double,
        innovationVariance: Double
    ): CusumState {
        val varianceFloor = max(1e-4, innovationVariance)
        val stdDev = sqrt(varianceFloor)
        val z = innovationError / stdDev

        // z < 0 indicates distance deficit (fuel economy drop): -z > 0 accumulates in gPlus
        // z > 0 indicates distance surplus (fuel economy jump): z > 0 accumulates in gMinus
        val newGPlus = max(0.0, prevState.gPlus + (-z) - kappa)
        val newGMinus = max(0.0, prevState.gMinus + z - kappa)

        val signal = when {
            newGPlus >= thresholdH -> EfficiencyChangeType.EFFICIENCY_DROP
            newGMinus >= thresholdH -> EfficiencyChangeType.EFFICIENCY_IMPROVEMENT
            else -> EfficiencyChangeType.NONE
        }

        return CusumState(
            gPlus = newGPlus,
            gMinus = newGMinus,
            lastStandardizedResidual = z,
            signal = signal
        )
    }

    /**
     * Process sequence of innovations (innovation error, innovation variance).
     */
    fun processSequence(
        innovations: List<Pair<Double, Double>>
    ): List<CusumState> {
        val states = mutableListOf<CusumState>()
        var current = CusumState()

        for ((error, variance) in innovations) {
            current = update(current, error, variance)
            states.add(current)
        }

        return states
    }
}
