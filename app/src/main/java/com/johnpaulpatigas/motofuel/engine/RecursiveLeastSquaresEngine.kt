package com.johnpaulpatigas.motofuel.engine

import kotlin.math.max
import kotlin.math.sqrt

/**
 * State container for the Recursive Least Squares (RLS) parameter estimator.
 *
 * @property theta Parameter estimate representing fuel economy (km/L).
 * @property covariance Error covariance matrix scalar (P).
 * @property sampleCount Number of valid intervals processed so far.
 * @property residualSumOfSquares Cumulative squared error of predictions.
 */
data class RlsState(
    val theta: Double = 0.0,
    val covariance: Double = 100.0,
    val sampleCount: Int = 0,
    val residualSumOfSquares: Double = 0.0
) {
    /**
     * Standard error of the estimated parameter (km/L).
     */
    val standardError: Double
        get() {
            if (sampleCount <= 1) return 0.0
            val varianceEstimate = residualSumOfSquares / max(1, sampleCount - 1)
            val se = sqrt(max(0.0, varianceEstimate * covariance))
            return if (se.isNaN() || se.isInfinite()) 0.0 else se
        }

    /**
     * 95% margin of error assuming approximate normality (1.96 * SE).
     */
    val marginOfError95: Double
        get() = 1.96 * standardError
}

/**
 * On-device online machine learning engine using Recursive Least Squares (RLS) with exponential forgetting.
 *
 * Models the relationship: Distance (km) = theta * Fuel (L), where theta is km/L.
 * Updates state recursively for each new observation (distance, fuel).
 */
class RecursiveLeastSquaresEngine(
    private val forgettingFactor: Double = 0.95
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

        // Prior prediction and error
        val priorTheta = state.theta
        val priorP = state.covariance
        val prediction = priorTheta * x
        val error = y - prediction

        // Gain vector K = (P * x) / (lambda + x^2 * P)
        val denominator = forgettingFactor + (x * x * priorP)
        if (denominator <= 1e-12) {
            return state
        }
        val gain = (priorP * x) / denominator

        // Update parameter theta and covariance P
        var updatedTheta = priorTheta + (gain * error)
        var updatedP = (1.0 / forgettingFactor) * (1.0 - gain * x) * priorP

        // Guard against negative or infinite values
        if (updatedTheta < 0.0 || updatedTheta.isNaN() || updatedTheta.isInfinite()) {
            updatedTheta = if (state.sampleCount > 0) priorTheta else (y / x)
        }
        if (updatedP < 1e-6 || updatedP.isNaN() || updatedP.isInfinite()) {
            updatedP = 1e-3
        }

        val newSampleCount = state.sampleCount + 1
        val currentError = y - (updatedTheta * x)
        val newRss = state.residualSumOfSquares + (currentError * currentError)

        return RlsState(
            theta = updatedTheta,
            covariance = updatedP,
            sampleCount = newSampleCount,
            residualSumOfSquares = newRss
        )
    }
}
