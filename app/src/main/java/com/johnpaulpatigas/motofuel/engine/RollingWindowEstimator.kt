package com.johnpaulpatigas.motofuel.engine

import kotlin.math.max

/**
 * Rolling-window cumulative fuel economy estimator.
 *
 * Computes \theta_{window, k} = \sum_{i \in W_k} D_i / \sum_{i \in W_k} A_i
 * over the latest W valid observations up to step k.
 *
 * @param windowSize Number of latest valid observations W to include in window.
 */
class RollingWindowEstimator(val windowSize: Int = 3) {
    init {
        require(windowSize > 0) { "Window size must be positive." }
    }

    /**
     * Compute rolling window fuel economy (km/L) for each step in a sequence of observations (distance km, fuel added L).
     */
    fun processSequence(observations: List<Pair<Double, Double>>): List<Double?> {
        if (observations.isEmpty()) return emptyList()

        val results = ArrayList<Double?>(observations.size)
        for (k in observations.indices) {
            val startIndex = max(0, k - windowSize + 1)
            val window = observations.subList(startIndex, k + 1)

            var sumD = 0.0
            var sumA = 0.0
            for ((dist, fuel) in window) {
                if (dist > 0.0 && fuel > 0.0) {
                    sumD += dist
                    sumA += fuel
                }
            }

            if (sumA > 0.0 && sumD > 0.0) {
                results.add(sumD / sumA)
            } else {
                results.add(null)
            }
        }

        return results
    }

    /**
     * Compute the latest rolling window fuel economy (km/L) for a list of observations.
     */
    fun evaluateLatest(observations: List<Pair<Double, Double>>): Double? {
        if (observations.isEmpty()) return null
        val startIndex = max(0, observations.size - windowSize)
        val window = observations.subList(startIndex, observations.size)

        var sumD = 0.0
        var sumA = 0.0
        for ((dist, fuel) in window) {
            if (dist > 0.0 && fuel > 0.0) {
                sumD += dist
                sumA += fuel
            }
        }

        return if (sumA > 0.0 && sumD > 0.0) sumD / sumA else null
    }
}
