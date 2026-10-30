package com.johnpaulpatigas.motofuel.engine

import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class MathematicalAuditTest {

    private val reportBuilder = StringBuilder()

    @Test
    fun `run full audit simulations`() {
        reportBuilder.appendLine("==========================================================================")
        reportBuilder.appendLine("       MOTOFUEL RLS SECOND-STAGE MATHEMATICAL AUDIT SIMULATION SUITE      ")
        reportBuilder.appendLine("==========================================================================")

        runInitializationComparison()
        runSample1OutlierComparison()
        runHeavyPartialFillVarianceComparison()
        runCoverageValidation()
        runOutOfSampleValidation()

        val outputFile = File(System.getProperty("java.io.tmpdir"), "audit_results.txt")
        outputFile.writeText(reportBuilder.toString())
    }

    private fun runInitializationComparison() {
        reportBuilder.appendLine("\n--- ISSUE 1: INITIALIZATION STRATEGY COMPARISON ---")
        val rng = Random(42)
        val trueTheta = 35.0 // km/L
        val numRuns = 2000
        val sequenceLength = 10

        val steps = listOf(1, 2, 3, 5, 10)
        
        val sqErrorsCurrent = mutableMapOf<Int, Double>().apply { steps.forEach { put(it, 0.0) } }
        val sqErrorsPrior25 = mutableMapOf<Int, Double>().apply { steps.forEach { put(it, 0.0) } }
        val sqErrorsPrior100 = mutableMapOf<Int, Double>().apply { steps.forEach { put(it, 0.0) } }
        val sqErrorsPrior10 = mutableMapOf<Int, Double>().apply { steps.forEach { put(it, 0.0) } }

        for (run in 0 until numRuns) {
            var prevTankDeviation = rng.nextGaussian() * 0.5
            val obs = mutableListOf<Pair<Double, Double>>()

            for (i in 0 until sequenceLength) {
                val trueFuelConsumed = 2.0 + rng.nextDouble() * 6.0 // 2L to 8L
                val currentTankDeviation = rng.nextGaussian() * 0.5
                val fuelAdded = max(0.5, trueFuelConsumed + currentTankDeviation - prevTankDeviation)
                prevTankDeviation = currentTankDeviation
                
                val distNoise = rng.nextGaussian() * 3.0 // 3km dist noise
                val dist = max(10.0, trueTheta * trueFuelConsumed + distNoise)
                obs.add(Pair(dist, fuelAdded))
            }

            // 1. Current Engine
            val currentEngine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
            val currentStates = currentEngine.processSequence(obs)
            steps.forEach { k ->
                val est = currentStates[k - 1].theta
                sqErrorsCurrent[k] = sqErrorsCurrent[k]!! + (est - trueTheta).pow(2)
            }

            // 2. Prior-based Engine (P0 = 25.0, theta0 = 30.0)
            val prior25States = processPriorBased(obs, priorTheta = 30.0, priorP = 25.0)
            steps.forEach { k ->
                val est = prior25States[k - 1].theta
                sqErrorsPrior25[k] = sqErrorsPrior25[k]!! + (est - trueTheta).pow(2)
            }

            // 2b. Prior-based Engine (P0 = 100.0, theta0 = 30.0)
            val prior100States = processPriorBased(obs, priorTheta = 30.0, priorP = 100.0)
            steps.forEach { k ->
                val est = prior100States[k - 1].theta
                sqErrorsPrior100[k] = sqErrorsPrior100[k]!! + (est - trueTheta).pow(2)
            }

            // 2c. Prior-based Engine (P0 = 10.0, theta0 = 30.0)
            val prior10States = processPriorBased(obs, priorTheta = 30.0, priorP = 10.0)
            steps.forEach { k ->
                val est = prior10States[k - 1].theta
                sqErrorsPrior10[k] = sqErrorsPrior10[k]!! + (est - trueTheta).pow(2)
            }
        }

        reportBuilder.appendLine("RMSE across $numRuns Monte Carlo runs (True theta = $trueTheta km/L):")
        reportBuilder.appendLine("%-10s | %-12s | %-15s | %-15s | %-15s".format("Step (k)", "Current RLS", "Prior(P=25)", "Prior(P=100)", "Prior(P=10)"))
        reportBuilder.appendLine("-".repeat(75))
        steps.forEach { k ->
            val rmseCurrent = sqrt(sqErrorsCurrent[k]!! / numRuns)
            val rmseP25 = sqrt(sqErrorsPrior25[k]!! / numRuns)
            val rmseP100 = sqrt(sqErrorsPrior100[k]!! / numRuns)
            val rmseP10 = sqrt(sqErrorsPrior10[k]!! / numRuns)
            reportBuilder.appendLine("%-10d | %-12.3f | %-15.3f | %-15.3f | %-15.3f".format(k, rmseCurrent, rmseP25, rmseP100, rmseP10))
        }
    }

    private fun runSample1OutlierComparison() {
        reportBuilder.appendLine("\n--- ISSUE 1B: SAMPLE 1 EXTREME OUTLIER RECOVERY ---")
        val trueTheta = 35.0
        val obs = listOf(
            Pair(150.0, 0.5), // Extremely noisy partial fill: 300.0 km/L on sample 1!
            Pair(350.0, 10.0), // 35.0 km/L
            Pair(350.0, 10.0), // 35.0 km/L
            Pair(350.0, 10.0), // 35.0 km/L
            Pair(350.0, 10.0)  // 35.0 km/L
        )

        val currentEngine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
        val currentStates = currentEngine.processSequence(obs)

        val prior25States = processPriorBased(obs, priorTheta = 30.0, priorP = 25.0)

        reportBuilder.appendLine("Outlier on Sample 1 (150 km on 0.5L = 300 km/L). True theta = 35.0 km/L:")
        reportBuilder.appendLine("%-10s | %-20s | %-20s".format("Step (k)", "Current RLS (theta)", "Prior Init (P=25)"))
        reportBuilder.appendLine("-".repeat(55))
        for (k in 1..obs.size) {
            reportBuilder.appendLine("%-10d | %-20.2f | %-20.2f".format(k, currentStates[k - 1].theta, prior25States[k - 1].theta))
        }
    }

    private fun runHeavyPartialFillVarianceComparison() {
        reportBuilder.appendLine("\n--- ISSUE 2B: HEAVY PARTIAL FILL VARIANCE & COVERAGE ---")
        val rng = Random(888)
        val trueTheta = 35.0
        val numRuns = 2000
        val sequenceLength = 8

        var coverageCurrent = 0
        var coverageDecoupled = 0
        var sumSeCurrent = 0.0
        var sumSeDecoupled = 0.0

        for (run in 0 until numRuns) {
            var prevTankDev = rng.nextGaussian() * 1.2 // High tank fluctuation 1.2L
            val obs = mutableListOf<Pair<Double, Double>>()

            for (i in 0 until sequenceLength) {
                val trueFuel = 2.0 + rng.nextDouble() * 3.0 // small fills 2-5L
                val tankDev = rng.nextGaussian() * 1.2
                val fuel = max(0.3, trueFuel + tankDev - prevTankDev)
                prevTankDev = tankDev

                val dist = max(10.0, trueTheta * trueFuel + rng.nextGaussian() * 5.0)
                obs.add(Pair(dist, fuel))
            }

            val statesCurr = processRssVariant(obs, RssVariant.CURRENT)
            val finalCurr = statesCurr.last()
            if (abs(finalCurr.theta - trueTheta) <= finalCurr.marginOfError95) {
                coverageCurrent++
            }
            sumSeCurrent += finalCurr.standardError

            val statesDec = processRssVariant(obs, RssVariant.DECOUPLED_ROBUST)
            val finalDec = statesDec.last()
            if (abs(finalDec.theta - trueTheta) <= finalDec.marginOfError95) {
                coverageDecoupled++
            }
            sumSeDecoupled += finalDec.standardError
        }

        reportBuilder.appendLine("Heavy Partial Fill Simulation ($sequenceLength refuels, sigma_V = 1.2L):")
        reportBuilder.appendLine("Current Huber RSS    : Mean SE = ${"%.4f".format(sumSeCurrent / numRuns)}, 95% Coverage = ${"%.2f".format(coverageCurrent * 100.0 / numRuns)}%")
        reportBuilder.appendLine("Decoupled Robust RSS : Mean SE = ${"%.4f".format(sumSeDecoupled / numRuns)}, 95% Coverage = ${"%.2f".format(coverageDecoupled * 100.0 / numRuns)}%")
    }

    private fun processPriorBased(
        observations: List<Pair<Double, Double>>,
        priorTheta: Double,
        priorP: Double,
        forgettingFactor: Double = 0.98,
        huberThreshold: Double = 3.0
    ): List<RlsState> {
        val states = mutableListOf<RlsState>()
        var theta = priorTheta
        var P = priorP
        var neff = 0.0
        var rss = 0.0
        var count = 0

        for ((y, x) in observations) {
            val pred = theta * x
            val rawError = y - pred

            val priorVar = if (neff > 1.0) rss / (neff - 1.0) else 100.0
            val predVar = max(1e-6, priorVar * (1.0 + x * x * P))
            val predStd = sqrt(predVar)
            val stdRes = if (predStd > 0.0) abs(rawError) / predStd else 0.0

            val error = if (stdRes > huberThreshold && count >= 2) {
                rawError * (huberThreshold / stdRes)
            } else {
                rawError
            }

            val denom = forgettingFactor + x * x * P
            val gain = (P * x) / denom

            theta += gain * error
            P = (1.0 / forgettingFactor) * (1.0 - gain * x) * P

            count++
            neff = forgettingFactor * neff + 1.0
            val postGain = max(0.0, 1.0 - gain * x)
            rss = forgettingFactor * rss + postGain * error * error

            states.add(
                RlsState(
                    theta = theta,
                    covariance = P,
                    sampleCount = count,
                    residualSumOfSquares = rss,
                    effectiveSampleSize = neff
                )
            )
        }
        return states
    }

    private enum class RssVariant { CURRENT, RAW_INNOVATION, DECOUPLED_ROBUST }

    private fun processRssVariant(
        observations: List<Pair<Double, Double>>,
        variant: RssVariant,
        forgettingFactor: Double = 0.98,
        huberThreshold: Double = 3.0
    ): List<RlsState> {
        val states = mutableListOf<RlsState>()
        var theta = 0.0
        var P = 100.0
        var neff = 0.0
        var rss = 0.0
        var count = 0

        for ((y, x) in observations) {
            val empiricalRatio = y / x
            if (count == 0 || theta <= 0.0) {
                val initialP = P / (forgettingFactor + x * x * P)
                theta = empiricalRatio
                P = initialP
                count = 1
                neff = 1.0
                rss = 0.0
                states.add(RlsState(theta, P, count, rss, neff))
                continue
            }

            val pred = theta * x
            val rawError = y - pred

            val priorVar = if (neff > 1.0) rss / (neff - 1.0) else 100.0
            val predVar = max(1e-6, priorVar * (1.0 + x * x * P))
            val predStd = sqrt(predVar)
            val stdRes = if (predStd > 0.0) abs(rawError) / predStd else 0.0

            val huberError = if (stdRes > huberThreshold && count >= 2) {
                rawError * (huberThreshold / stdRes)
            } else {
                rawError
            }

            val denom = forgettingFactor + x * x * P
            val gain = (P * x) / denom

            // Parameter update ALWAYS uses huberError to protect parameter theta
            theta += gain * huberError
            P = (1.0 / forgettingFactor) * (1.0 - gain * x) * P

            count++
            neff = forgettingFactor * neff + 1.0
            val postGain = max(0.0, 1.0 - gain * x)

            val errorForRss = when (variant) {
                RssVariant.CURRENT -> huberError
                RssVariant.RAW_INNOVATION -> rawError
                RssVariant.DECOUPLED_ROBUST -> {
                    // Decoupled: Allow raw innovation up to 5.0 sigma (catches partial fills, rejects typo disasters)
                    if (stdRes > 5.0 && count >= 2) {
                        rawError * (5.0 / stdRes)
                    } else {
                        rawError
                    }
                }
            }

            rss = (forgettingFactor * rss) + (postGain * errorForRss * errorForRss)

            states.add(RlsState(theta, P, count, rss, neff))
        }
        return states
    }

    private fun runCoverageValidation() {
        reportBuilder.appendLine("\n--- ISSUE 3: EMPIRICAL COVERAGE AT VARIOUS SAMPLE SIZES ---")
        val rng = Random(999)
        val trueTheta = 38.0
        val numRuns = 2000
        val sampleSizes = listOf(2, 3, 5, 10, 20)

        for (N in sampleSizes) {
            var coveredCurrent = 0
            var coveredDecoupled = 0

            for (run in 0 until numRuns) {
                var prevTankDev = rng.nextGaussian() * 0.6
                val obs = mutableListOf<Pair<Double, Double>>()

                for (i in 0 until N) {
                    val trueFuel = 2.0 + rng.nextDouble() * 6.0
                    val tankDev = rng.nextGaussian() * 0.6
                    val fuel = max(0.5, trueFuel + tankDev - prevTankDev)
                    prevTankDev = tankDev

                    val dist = max(10.0, trueTheta * trueFuel + rng.nextGaussian() * 3.5)
                    obs.add(Pair(dist, fuel))
                }

                val stateCurr = processRssVariant(obs, RssVariant.CURRENT).last()
                if (abs(stateCurr.theta - trueTheta) <= stateCurr.marginOfError95) {
                    coveredCurrent++
                }

                val stateDecoupled = processRssVariant(obs, RssVariant.DECOUPLED_ROBUST).last()
                if (abs(stateDecoupled.theta - trueTheta) <= stateDecoupled.marginOfError95) {
                    coveredDecoupled++
                }
            }

            reportBuilder.appendLine("N = %2d refuels | Current RLS Coverage: %5.1f%% | Decoupled Robust RSS Coverage: %5.1f%%".format(
                N, coveredCurrent * 100.0 / numRuns, coveredDecoupled * 100.0 / numRuns
            ))
        }
    }

    private fun runOutOfSampleValidation() {
        reportBuilder.appendLine("\n--- ISSUE 4: OUT-OF-SAMPLE VALIDATION ---")
        val rng = Random(2026)
        val trueTheta = 32.0
        val numRuns = 1000
        val trainSize = 5
        val testSize = 5

        var totalAbsErrCurrent = 0.0
        var totalSqErrCurrent = 0.0

        var totalAbsErrPriorInit = 0.0
        var totalSqErrPriorInit = 0.0

        var totalAbsErrCumul = 0.0
        var totalSqErrCumul = 0.0

        var totalTestObs = 0

        for (run in 0 until numRuns) {
            var prevTankDev = rng.nextGaussian() * 0.5
            val trainObs = mutableListOf<Pair<Double, Double>>()
            val testObs = mutableListOf<Pair<Double, Double>>()

            for (i in 0 until trainSize) {
                val trueFuel = 3.0 + rng.nextDouble() * 5.0
                val tankDev = rng.nextGaussian() * 0.5
                val fuel = max(0.5, trueFuel + tankDev - prevTankDev)
                prevTankDev = tankDev

                val dist = max(10.0, trueTheta * trueFuel + rng.nextGaussian() * 3.0)
                trainObs.add(Pair(dist, fuel))
            }

            for (i in 0 until testSize) {
                val trueFuel = 3.0 + rng.nextDouble() * 5.0
                val tankDev = rng.nextGaussian() * 0.5
                val fuel = max(0.5, trueFuel + tankDev - prevTankDev)
                prevTankDev = tankDev

                val dist = max(10.0, trueTheta * trueFuel + rng.nextGaussian() * 3.0)
                testObs.add(Pair(dist, fuel))
            }

            val engineCurrent = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
            val thetaCurrent = engineCurrent.processSequence(trainObs).last().theta

            val thetaPriorInit = processPriorBased(trainObs, priorTheta = 30.0, priorP = 25.0).last().theta

            val sumTrainDist = trainObs.sumOf { it.first }
            val sumTrainFuel = trainObs.sumOf { it.second }
            val thetaCumul = sumTrainDist / sumTrainFuel

            for ((yTest, xTest) in testObs) {
                totalTestObs++

                val predCurrent = thetaCurrent * xTest
                val errCurrent = yTest - predCurrent
                totalAbsErrCurrent += abs(errCurrent)
                totalSqErrCurrent += errCurrent * errCurrent

                val predPrior = thetaPriorInit * xTest
                val errPrior = yTest - predPrior
                totalAbsErrPriorInit += abs(errPrior)
                totalSqErrPriorInit += errPrior * errPrior

                val predCumul = thetaCumul * xTest
                val errCumul = yTest - predCumul
                totalAbsErrCumul += abs(errCumul)
                totalSqErrCumul += errCumul * errCumul
            }
        }

        reportBuilder.appendLine("Out-of-Sample Performance across $totalTestObs test observations ($numRuns runs, Train N=$trainSize, Test M=$testSize):")
        reportBuilder.appendLine("Model                       | MAE (km) | RMSE (km)")
        reportBuilder.appendLine("-".repeat(50))
        reportBuilder.appendLine("%-27s | %-8.3f | %-8.3f".format("Current RLS Engine", totalAbsErrCurrent / totalTestObs, sqrt(totalSqErrCurrent / totalTestObs)))
        reportBuilder.appendLine("%-27s | %-8.3f | %-8.3f".format("Prior-Init RLS (P0=25)", totalAbsErrPriorInit / totalTestObs, sqrt(totalSqErrPriorInit / totalTestObs)))
        reportBuilder.appendLine("%-27s | %-8.3f | %-8.3f".format("Cumulative Baseline", totalAbsErrCumul / totalTestObs, sqrt(totalSqErrCumul / totalTestObs)))
    }
}
