package com.johnpaulpatigas.motofuel.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * State container for the Recursive Least Squares (RLS) parameter estimator.
 *
 * @property theta Parameter estimate representing fuel economy (km/L).
 * @property covariance Error covariance matrix scalar (P).
 * @property sampleCount Total number of valid intervals processed so far.
 * @property residualSumOfSquares Exponentially weighted cumulative squared error of predictions.
 * @property effectiveSampleSize Exponentially weighted effective sample size (N_eff).
 */
data class RlsState(
    val theta: Double = 0.0,
    val covariance: Double = 100.0,
    val sampleCount: Int = 0,
    val residualSumOfSquares: Double = 0.0,
    val effectiveSampleSize: Double = 0.0
) {
    /**
     * Exponentially weighted standard error of estimated fuel economy (km/L).
     */
    val standardError: Double
        get() {
            if (sampleCount < 2 || effectiveSampleSize <= 1.0) return 0.0
            val varianceEstimate = residualSumOfSquares / max(1.0, effectiveSampleSize - 1.0)
            val se = sqrt(max(0.0, varianceEstimate * covariance))
            return if (se.isNaN() || se.isInfinite()) 0.0 else se
        }

    /**
     * 95% margin of error using Student's t-distribution critical values
     * based on effective degrees of freedom.
     */
    val marginOfError95: Double
        get() {
            val se = standardError
            if (se <= 0.0) return 0.0
            val dof = max(1, (effectiveSampleSize - 1.0).roundToInt())
            val tCritical = getTCriticalValue95(dof)
            val moe = tCritical * se
            return if (moe.isNaN() || moe.isInfinite()) 0.0 else moe
        }

    companion object {
        fun getTCriticalValue95(dof: Int): Double {
            return when (dof) {
                1 -> 12.706
                2 -> 4.303
                3 -> 3.182
                4 -> 2.776
                5 -> 2.571
                6 -> 2.447
                7 -> 2.365
                8 -> 2.306
                9 -> 2.262
                10 -> 2.228
                in 11..15 -> 2.15
                in 16..29 -> 2.05
                else -> 1.96
            }
        }
    }
}

/**
 * On-device online machine learning engine using Recursive Least Squares (RLS)
 * with exponential forgetting, robust Huber parameter updating, and decoupled
 * residual variance estimation.
 *
 * Models the relationship: Distance (km) = theta * Fuel (L), where theta is km/L.
 *
 * @param forgettingFactor Exponential forgetting factor lambda (default 0.98, optimal from comparative empirical validation).
 * @param huberThreshold Standardized residual threshold for robust Huber loss gain attenuation (default 3.0).
 * @param initialThetaPrior Optional prior fuel economy estimate (km/L). If > 0, sample 1 updates from this prior.
 * @param initialCovariancePrior Covariance matrix scalar associated with initial prior.
 * @param rssOutlierThreshold Standardized residual threshold for residual variance accumulation (default 5.0).
 */
class RecursiveLeastSquaresEngine(
    val forgettingFactor: Double = 0.98,
    private val huberThreshold: Double = 3.0,
    val initialThetaPrior: Double = 0.0,
    val initialCovariancePrior: Double = 25.0,
    private val rssOutlierThreshold: Double = 5.0
) {
    /**
     * Process a sequence of observations (interval distance km, fuel added L) sequentially from state baseline.
     */
    fun processSequence(observations: List<Pair<Double, Double>>): List<RlsState> {
        val states = mutableListOf<RlsState>()
        var currentState = RlsState()

        for ((distance, fuel) in observations) {
            currentState = update(currentState, distance, fuel)
            states.add(currentState)
        }

        return states
    }

    /**
     * Update RLS state with a single observation (distance km, fuel added L).
     */
    fun update(state: RlsState, distanceKm: Double, litersAdded: Double): RlsState {
        if (litersAdded <= 0.0 || distanceKm <= 0.0) {
            return state
        }

        val x = litersAdded
        val y = distanceKm
        val empiricalRatio = y / x

        // Handle initial observation explicitly
        if (state.sampleCount == 0 || state.theta <= 0.0) {
            if (initialThetaPrior > 0.0 && state.sampleCount == 0) {
                // Prior-based initialization on sample 1: update from initialThetaPrior
                val priorTheta = initialThetaPrior
                val priorP = initialCovariancePrior
                val prediction = priorTheta * x
                val rawError = y - prediction

                val denominator = forgettingFactor + (x * x * priorP)
                val gain = if (denominator > 1e-12) (priorP * x) / denominator else 0.0

                var updatedTheta = priorTheta + (gain * rawError)
                var updatedP = (1.0 / forgettingFactor) * (1.0 - gain * x) * priorP

                if (updatedTheta <= 0.0 || updatedTheta.isNaN() || updatedTheta.isInfinite()) updatedTheta = priorTheta
                if (updatedP < 1e-6 || updatedP.isNaN() || updatedP.isInfinite()) updatedP = 1e-3

                val postGainFactor = max(0.0, 1.0 - gain * x)
                val newRss = postGainFactor * rawError * rawError

                return RlsState(
                    theta = updatedTheta,
                    covariance = updatedP,
                    sampleCount = 1,
                    residualSumOfSquares = newRss,
                    effectiveSampleSize = 1.0
                )
            } else {
                // Direct initial observation assignment with covariance floor to prevent premature lock-in
                val rawP = state.covariance / (forgettingFactor + x * x * state.covariance)
                val initialP = if (rawP.isNaN() || rawP.isInfinite()) 10.0 else max(2.5, rawP)
                return RlsState(
                    theta = empiricalRatio,
                    covariance = initialP,
                    sampleCount = 1,
                    residualSumOfSquares = 0.0,
                    effectiveSampleSize = 1.0
                )
            }
        }

        val priorTheta = state.theta
        val priorP = state.covariance
        val priorNeff = state.effectiveSampleSize
        val priorRss = state.residualSumOfSquares

        // Prior prediction and innovation residual
        val prediction = priorTheta * x
        val rawError = y - prediction

        // Standardized residual calculation based on prior innovation variance
        val priorVarianceEstimate = if (priorNeff > 1.0) priorRss / (priorNeff - 1.0) else 100.0
        val predVariance = max(1e-6, priorVarianceEstimate * (1.0 + x * x * priorP))
        val predStdDev = sqrt(predVariance)
        val standardizedResidual = if (predStdDev > 0.0) abs(rawError) / predStdDev else 0.0

        // Robust Huber loss gain attenuation for parameter theta update
        val errorForTheta = if (standardizedResidual > huberThreshold && state.sampleCount >= 2) {
            val huberWeight = huberThreshold / standardizedResidual
            rawError * huberWeight
        } else {
            rawError
        }

        // Decoupled residual calculation for variance (RSS) estimation
        // Allows untruncated partial-fill residual scatter up to 5.0 sigma while rejecting multi-sigma typos
        val errorForRss = if (standardizedResidual > rssOutlierThreshold && state.sampleCount >= 2) {
            val rssWeight = rssOutlierThreshold / standardizedResidual
            rawError * rssWeight
        } else {
            rawError
        }

        // Gain scalar calculation: K = (P * x) / (lambda + x^2 * P)
        val denominator = forgettingFactor + (x * x * priorP)
        if (denominator <= 1e-12 || denominator.isNaN()) {
            return state
        }
        val gain = (priorP * x) / denominator

        // Parameter theta update and covariance P update
        var updatedTheta = priorTheta + (gain * errorForTheta)
        var updatedP = (1.0 / forgettingFactor) * (1.0 - gain * x) * priorP

        // Numerical stability guards
        if (updatedTheta <= 0.0 || updatedTheta.isNaN() || updatedTheta.isInfinite()) {
            updatedTheta = priorTheta
        }
        if (updatedP < 1e-6 || updatedP.isNaN() || updatedP.isInfinite()) {
            updatedP = 1e-3
        }

        val newSampleCount = state.sampleCount + 1
        val newNeff = (forgettingFactor * priorNeff) + 1.0

        // Decoupled exponentially discounted Residual Sum of Squares (RSS)
        val postGainFactor = max(0.0, 1.0 - gain * x)
        val newRss = (forgettingFactor * priorRss) + (postGainFactor * errorForRss * errorForRss)

        return RlsState(
            theta = updatedTheta,
            covariance = updatedP,
            sampleCount = newSampleCount,
            residualSumOfSquares = newRss,
            effectiveSampleSize = newNeff
        )
    }
}
