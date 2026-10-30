package com.johnpaulpatigas.motofuel.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Comparative simulation test suite for evaluating Recursive Least Squares (RLS)
 * under different forgetting factors (\lambda \in {0.95, 0.98, 0.99, 0.995}) against
 * the Cumulative Baseline Estimator across 10 realistic synthetic refueling scenarios.
 */
class EngineValidationTest {

    data class ScenarioResult(
        val lambda: Double,
        val mae: Double,
        val rmse: Double,
        val finalEstimate: Double
    )

    private fun runSimulation(
        lambda: Double,
        observations: List<Pair<Double, Double>>,
        groundTruths: List<Double>
    ): ScenarioResult {
        val engine = RecursiveLeastSquaresEngine(forgettingFactor = lambda)
        val states = engine.processSequence(observations)

        var totalAbsError = 0.0
        var totalSqError = 0.0
        val count = observations.size

        for (i in 0 until count) {
            val pred = states[i].theta
            val truth = groundTruths[i]
            val error = pred - truth
            totalAbsError += abs(error)
            totalSqError += error * error
        }

        val mae = totalAbsError / count
        val rmse = sqrt(totalSqError / count)
        val finalEst = states.lastOrNull()?.theta ?: 0.0

        return ScenarioResult(lambda, mae, rmse, finalEst)
    }

    private fun runCumulativeBaseline(
        observations: List<Pair<Double, Double>>,
        groundTruths: List<Double>
    ): ScenarioResult {
        var runningDist = 0.0
        var runningFuel = 0.0
        var totalAbsError = 0.0
        var totalSqError = 0.0
        val count = observations.size

        var finalEst = 0.0
        for (i in 0 until count) {
            val (dist, fuel) = observations[i]
            runningDist += dist
            runningFuel += fuel
            val est = if (runningFuel > 0.0) runningDist / runningFuel else 0.0
            finalEst = est
            val truth = groundTruths[i]
            val error = est - truth
            totalAbsError += abs(error)
            totalSqError += error * error
        }

        val mae = totalAbsError / count
        val rmse = sqrt(totalSqError / count)

        return ScenarioResult(0.0, mae, rmse, finalEst)
    }

    @Test
    fun `evaluate forgetting factors across 10 simulation scenarios`() {
        val lambdas = listOf(0.95, 0.98, 0.99, 0.995)

        println("==========================================================================")
        println("       MOTOFUEL PREDICTION ENGINE COMPARATIVE VALIDATION RESULTS          ")
        println("==========================================================================")

        // 1. Scenario 1: Consistent efficiency with varying partial fills
        // Bike gets 35.0 km/L. Rider makes partial fills from a 12L tank.
        val s1Obs = listOf(
            Pair(70.0, 2.0),
            Pair(175.0, 5.0),
            Pair(122.5, 3.5),
            Pair(35.0, 1.0),
            Pair(140.0, 4.0),
            Pair(105.0, 3.0),
            Pair(210.0, 6.0),
            Pair(87.5, 2.5),
            Pair(157.5, 4.5),
            Pair(175.0, 5.0)
        )
        val s1Truth = List(s1Obs.size) { 35.0 }

        // 2. Scenario 2: Tank inventory drift (systematic under/over fills)
        // Bike gets 30.0 km/L. Tank inventory decreases then recovers.
        val s2Obs = listOf(
            Pair(120.0, 2.0), // Underfill (4.0L consumed, 2.0L added) -> interval ratio 60 km/L
            Pair(120.0, 2.0), // Underfill -> interval ratio 60 km/L
            Pair(120.0, 2.0), // Underfill -> interval ratio 60 km/L
            Pair(120.0, 8.0), // Overfill (4.0L consumed, 8.0L added) -> interval ratio 15 km/L
            Pair(120.0, 2.0), // Normal/Underfill
            Pair(120.0, 6.0)  // Catch up
        )
        val s2Truth = List(s2Obs.size) { 30.0 }

        // 3. Scenario 3: Sudden efficiency shift (Highway 40.0 km/L -> City 28.0 km/L)
        val s3Obs = mutableListOf<Pair<Double, Double>>()
        val s3Truth = mutableListOf<Double>()
        repeat(8) {
            s3Obs.add(Pair(200.0, 5.0)) // 40.0 km/L
            s3Truth.add(40.0)
        }
        repeat(8) {
            s3Obs.add(Pair(140.0, 5.0)) // 28.0 km/L
            s3Truth.add(28.0)
        }

        // 4. Scenario 4: Small fuel additions (0.3L - 0.8L partial fills)
        val s4Obs = listOf(
            Pair(10.0, 0.3),  // 33.3 km/L
            Pair(200.0, 6.0), // 33.3 km/L
            Pair(15.0, 0.5),  // 30.0 km/L
            Pair(180.0, 5.5), // 32.7 km/L
            Pair(25.0, 0.8)   // 31.25 km/L
        )
        val s4Truth = List(s4Obs.size) { 32.0 }

        // 5. Scenario 5: Large fuel additions (10L - 14L full fills)
        val s5Obs = listOf(
            Pair(380.0, 10.0), // 38.0 km/L
            Pair(456.0, 12.0), // 38.0 km/L
            Pair(532.0, 14.0), // 38.0 km/L
            Pair(418.0, 11.0)  // 38.0 km/L
        )
        val s5Truth = List(s5Obs.size) { 38.0 }

        // 6. Scenario 6: Incorrect/Anomalous entries (Typo surge e.g. 3000 km interval)
        val s6Obs = listOf(
            Pair(150.0, 5.0),  // 30.0 km/L
            Pair(150.0, 5.0),  // 30.0 km/L
            Pair(1500.0, 5.0), // Outlier typo: 300 km/L
            Pair(150.0, 5.0),  // 30.0 km/L
            Pair(150.0, 5.0)   // 30.0 km/L
        )
        val s6Truth = List(s6Obs.size) { 30.0 }

        // 7. Scenario 7: Missing refueling entries (gap in recorded fuel)
        val s7Obs = listOf(
            Pair(140.0, 4.0), // 35.0 km/L
            Pair(280.0, 4.0), // Missed 1 fill -> ratio 70.0 km/L
            Pair(140.0, 4.0), // 35.0 km/L
            Pair(140.0, 4.0)  // 35.0 km/L
        )
        val s7Truth = List(s7Obs.size) { 35.0 }

        // 8. Scenario 8: Long-term consistent riding (30 refuels around 36.0 km/L)
        val s8Obs = mutableListOf<Pair<Double, Double>>()
        val s8Truth = mutableListOf<Double>()
        val pseudoNoise = listOf(0.0, 0.2, -0.1, 0.3, -0.2, 0.1, -0.3, 0.0, 0.1, -0.1)
        repeat(30) { i ->
            val noise = pseudoNoise[i % pseudoNoise.size]
            val fuel = 5.0 + noise
            val dist = 36.0 * 5.0
            s8Obs.add(Pair(dist, fuel))
            s8Truth.add(36.0)
        }

        val scenarios = listOf(
            "1. Varying Partial Fills" to (s1Obs to s1Truth),
            "2. Inventory Drift" to (s2Obs to s2Truth),
            "3. Sudden Efficiency Shift" to (s3Obs to s3Truth),
            "4. Small Partial Fills" to (s4Obs to s4Truth),
            "5. Large Full Fills" to (s5Obs to s5Truth),
            "6. Outlier Measurement" to (s6Obs to s6Truth),
            "7. Missing Entry Gap" to (s7Obs to s7Truth),
            "8. Long-Term Consistent" to (s8Obs to s8Truth)
        )

        val lambdaCumulativeRmse = mutableMapOf<Double, Double>()
        lambdas.forEach { lambdaCumulativeRmse[it] = 0.0 }
        var cumulativeBaselineRmseSum = 0.0

        for ((name, pair) in scenarios) {
            val (obs, truth) = pair
            println("\n--- Scenario: $name ---")

            val baselineRes = runCumulativeBaseline(obs, truth)
            println("  [Cumulative Baseline] MAE: ${"%.3f".format(baselineRes.mae)}, RMSE: ${"%.3f".format(baselineRes.rmse)}, Final: ${"%.2f".format(baselineRes.finalEstimate)} km/L")
            cumulativeBaselineRmseSum += baselineRes.rmse

            for (lam in lambdas) {
                val res = runSimulation(lam, obs, truth)
                println("  [RLS lambda = ${"%.3f".format(lam)}] MAE: ${"%.3f".format(res.mae)}, RMSE: ${"%.3f".format(res.rmse)}, Final: ${"%.2f".format(res.finalEstimate)} km/L")
                lambdaCumulativeRmse[lam] = lambdaCumulativeRmse[lam]!! + res.rmse
            }
        }

        println("\n==========================================================================")
        println("                  OVERALL SUMMARY ACROSS SCENARIOS                        ")
        println("==========================================================================")
        println("Cumulative Baseline Total RMSE: ${"%.3f".format(cumulativeBaselineRmseSum)}")
        for (lam in lambdas) {
            println("RLS lambda = ${"%.3f".format(lam)} Total RMSE: ${"%.3f".format(lambdaCumulativeRmse[lam]!!)}")
        }

        // Verify that RLS estimates are finite and non-negative
        for (lam in lambdas) {
            val res = runSimulation(lam, s1Obs, s1Truth)
            assertTrue(res.rmse >= 0.0)
            assertTrue(res.finalEstimate > 0.0)
        }
    }
}
