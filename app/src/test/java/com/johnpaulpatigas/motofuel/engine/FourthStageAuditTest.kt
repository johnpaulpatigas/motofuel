package com.johnpaulpatigas.motofuel.engine

import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MotoFuel Fourth-Stage Audit Test Suite: Hybrid Estimator Integrity.
 * Rigorous mathematical and empirical audit of Cumulative, RLS, Sandwich, ESS, VFF, and Hybrid estimators.
 */
class FourthStageAuditTest {

    private val reportBuilder = StringBuilder()

    companion object {
        const val NUM_MC_RUNS = 10000
    }

    @Test
    fun `run fourth stage audit`() {
        reportBuilder.appendLine("==========================================================================")
        reportBuilder.appendLine("     MOTOFUEL FOURTH-STAGE AUDIT: HYBRID ESTIMATOR INTEGRITY RESULTS     ")
        reportBuilder.appendLine("==========================================================================")

        auditCumulativeEstimator()
        auditRlsCovarianceAndConfidenceIntervals()
        auditEffectiveSampleSize()
        auditAdaptiveForgetting()
        validateHybridArchitecture()
        auditExistingTestReport()

        val reportText = reportBuilder.toString()
        println(reportText)

        // Save report to system temporary directory
        File(System.getProperty("java.io.tmpdir"), "fourth_stage_audit_results.txt").writeText(reportText)
    }

    // -------------------------------------------------------------------------
    // 1. CUMULATIVE ESTIMATOR AUDIT
    // -------------------------------------------------------------------------
    private fun auditCumulativeEstimator() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("1. CUMULATIVE ESTIMATOR VARIANCE DECOMPOSITION & CONVERGENCE RATE AUDIT     ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val sampleSizes = listOf(2, 3, 5, 10, 20, 50, 100)
        val sigmaS = 0.8 // Tank inventory noise (Liters)
        val sigmaD = 3.0 // Distance noise (km)
        val meanFuel = 5.0 // Average fuel per interval (Liters)

        reportBuilder.appendLine("%-8s | %-16s | %-16s | %-16s | %-16s".format(
            "N", "MC Var(Theta)", "Theory Var Total", "Theory Var Inv O(1/N2)", "Theory Var Dist O(1/N)"
        ))
        reportBuilder.appendLine("-".repeat(82))

        for (N in sampleSizes) {
            val rng = Random((1000 + N).toLong())
            val thetaCumulList = mutableListOf<Double>()

            for (run in 0 until NUM_MC_RUNS) {
                var prevS = rng.nextGaussian() * sigmaS
                var sumD = 0.0
                var sumA = 0.0

                for (k in 0 until N) {
                    val trueFuel = meanFuel
                    val currentS = rng.nextGaussian() * sigmaS
                    val fuelAdded = max(0.1, trueFuel + currentS - prevS)
                    prevS = currentS

                    val distNoise = rng.nextGaussian() * sigmaD
                    val distanceKm = max(1.0, trueTheta * trueFuel + distNoise)

                    sumD += distanceKm
                    sumA += fuelAdded
                }

                val cumulTheta = sumD / sumA
                thetaCumulList.add(cumulTheta)
            }

            val mcMean = thetaCumulList.average()
            val mcVar = thetaCumulList.map { (it - mcMean).pow(2) }.average()

            // Theoretical variance components (Delta method)
            // V = N * meanFuel
            val totalV = N * meanFuel
            val varInvComponent = (2.0 * trueTheta.pow(2) * sigmaS.pow(2)) / totalV.pow(2) // O(1/N^2)
            val varDistComponent = (N * sigmaD.pow(2)) / totalV.pow(2) // O(1/N)
            val theoryTotalVar = varInvComponent + varDistComponent

            reportBuilder.appendLine("%-8d | %-16.6f | %-16.6f | %-16.6f | %-16.6f".format(
                N, mcVar, theoryTotalVar, varInvComponent, varDistComponent
            ))
        }
    }

    // -------------------------------------------------------------------------
    // 2. RLS COVARIANCE & CONFIDENCE INTERVAL AUDIT
    // -------------------------------------------------------------------------
    private fun auditRlsCovarianceAndConfidenceIntervals() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("2. RLS COVARIANCE & CONFIDENCE INTERVAL AUDIT ACROSS NOISE CONFIGURATIONS ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val sampleSizes = listOf(2, 3, 5, 10, 20, 50)

        val testScenarios = listOf(
            "Scenario A: Gaussian Noise + Independent Fills (sigma_S=0.8L, sigma_d=3km)" to
                    Triple(InventoryType.INDEPENDENT, NoiseType.GAUSSIAN, 0.8),
            "Scenario B: Heavy-Tailed Student-t Noise (df=3, sigma_S=0.8L)" to
                    Triple(InventoryType.INDEPENDENT, NoiseType.STUDENT_T, 0.8),
            "Scenario C: Persistent Random Walk Tank Noise (sigma_eta=0.5L)" to
                    Triple(InventoryType.RANDOM_WALK, NoiseType.GAUSSIAN, 0.5),
            "Scenario D: Mean-Reverting AR(1) Tank Noise (phi=0.7, sigma_eta=0.5L)" to
                    Triple(InventoryType.MEAN_REVERTING, NoiseType.GAUSSIAN, 0.5)
        )

        for ((scenName, scenTriple) in testScenarios) {
            val (invType, noiseType, sigS) = scenTriple
            reportBuilder.appendLine("\n--- $scenName ---")
            reportBuilder.appendLine("%-5s | %-12s %-10s | %-12s %-10s | %-12s %-10s | %-12s".format(
                "N", "Standard CI%", "AvgWidth", "Sandwich CI%", "AvgWidth", "HAC Lag1 CI%", "AvgWidth", "MC StdDev"
            ))
            reportBuilder.appendLine("-".repeat(95))

            for (N in sampleSizes) {
                val rng = Random((2000 + N + invType.ordinal * 100).toLong())
                var covStdCount = 0
                var covSandwichCount = 0
                var covHacCount = 0

                var sumWidthStd = 0.0
                var sumWidthSandwich = 0.0
                var sumWidthHac = 0.0

                val thetaList = mutableListOf<Double>()

                for (run in 0 until NUM_MC_RUNS) {
                    val obs = generateObservations(rng, trueTheta, N, invType, sigS, 3.0, 0.7, noiseType)
                    val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
                    val states = engine.processSequence(obs)
                    val finalState = states.last()
                    val estTheta = finalState.theta
                    thetaList.add(estTheta)

                    // 1. Standard RLS 95% MOE
                    val moeStd = finalState.marginOfError95
                    sumWidthStd += 2.0 * moeStd
                    if (abs(estTheta - trueTheta) <= moeStd) covStdCount++

                    // 2. Heteroskedasticity Sandwich MOE
                    val moeSandwich = computeSandwichMoe95(finalState, obs)
                    sumWidthSandwich += 2.0 * moeSandwich
                    if (abs(estTheta - trueTheta) <= moeSandwich) covSandwichCount++

                    // 3. HAC / Newey-West Lag-1 MOE
                    val moeHac = computeHacLag1Moe95(finalState, obs)
                    sumWidthHac += 2.0 * moeHac
                    if (abs(estTheta - trueTheta) <= moeHac) covHacCount++
                }

                val mcMean = thetaList.average()
                val mcStdDev = sqrt(thetaList.map { (it - mcMean).pow(2) }.average())

                val covStd = covStdCount * 100.0 / NUM_MC_RUNS
                val covSandwich = covSandwichCount * 100.0 / NUM_MC_RUNS
                val covHac = covHacCount * 100.0 / NUM_MC_RUNS

                val wStd = sumWidthStd / NUM_MC_RUNS
                val wSandwich = sumWidthSandwich / NUM_MC_RUNS
                val wHac = sumWidthHac / NUM_MC_RUNS

                reportBuilder.appendLine("%-5d | %-12.2f %-10.3f | %-12.2f %-10.3f | %-12.2f %-10.3f | %-12.4f".format(
                    N, covStd, wStd, covSandwich, wSandwich, covHac, wHac, mcStdDev
                ))
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. EFFECTIVE SAMPLE SIZE AUDIT
    // -------------------------------------------------------------------------
    private fun auditEffectiveSampleSize() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("3. EFFECTIVE SAMPLE SIZE (ESS) FORMULA AUDIT & DEGREES OF FREEDOM          ")
        reportBuilder.appendLine("==========================================================================")

        val lambdas = listOf(0.90, 0.95, 0.98, 0.99, 0.995)

        reportBuilder.appendLine("%-8s | %-18s | %-18s | %-20s".format(
            "Lambda", "Recursive Neff (inf)", "Kish Neff (inf)", "Ratio Kish / Recursive"
        ))
        reportBuilder.appendLine("-".repeat(70))

        for (lam in lambdas) {
            val neffRecInf = 1.0 / (1.0 - lam)
            val neffKishInf = (1.0 + lam) / (1.0 - lam)
            val ratio = neffKishInf / neffRecInf

            reportBuilder.appendLine("%-8.3f | %-18.2f | %-18.2f | %-20.2f".format(
                lam, neffRecInf, neffKishInf, ratio
            ))
        }

        reportBuilder.appendLine("\n--- Empirically Testing Student-t CI Coverage under Neff_rec vs Neff_Kish vs N_obs ---")
        val trueTheta = 35.0
        val sampleSizes = listOf(3, 5, 10, 20)
        val lam = 0.98

        reportBuilder.appendLine("%-5s | %-18s | %-18s | %-18s".format(
            "N", "Coverage (dof=N-1)", "Coverage (dof=Neff_rec-1)", "Coverage (dof=Neff_Kish-1)"
        ))
        reportBuilder.appendLine("-".repeat(68))

        for (N in sampleSizes) {
            val rng = Random((3000 + N).toLong())
            var covNCount = 0
            var covRecCount = 0
            var covKishCount = 0

            for (run in 0 until NUM_MC_RUNS) {
                val obs = generateObservations(rng, trueTheta, N, InventoryType.INDEPENDENT, 0.8, 3.0)
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = lam)
                val states = engine.processSequence(obs)
                val finalState = states.last()
                val est = finalState.theta
                val se = finalState.standardError

                if (se <= 0.0) continue

                // 1. dof = N - 1
                val dofN = max(1, N - 1)
                val tCritN = RlsState.getTCriticalValue95(dofN)
                if (abs(est - trueTheta) <= tCritN * se) covNCount++

                // 2. dof = Neff_rec - 1
                val dofRec = max(1, (finalState.effectiveSampleSize - 1.0).toInt())
                val tCritRec = RlsState.getTCriticalValue95(dofRec)
                if (abs(est - trueTheta) <= tCritRec * se) covRecCount++

                // 3. dof = Neff_Kish - 1
                val kishNeff = computeKishNeff(obs.size, lam)
                val dofKish = max(1, (kishNeff - 1.0).toInt())
                val tCritKish = RlsState.getTCriticalValue95(dofKish)
                if (abs(est - trueTheta) <= tCritKish * se) covKishCount++
            }

            reportBuilder.appendLine("%-5d | %-18.2f | %-18.2f | %-18.2f".format(
                N, covNCount * 100.0 / NUM_MC_RUNS, covRecCount * 100.0 / NUM_MC_RUNS, covKishCount * 100.0 / NUM_MC_RUNS
            ))
        }
    }

    // -------------------------------------------------------------------------
    // 4. ADAPTIVE FORGETTING AUDIT
    // -------------------------------------------------------------------------
    private fun auditAdaptiveForgetting() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("4. VARIABLE FORGETTING FACTOR (VFF) AUDIT: DISTINGUISHING NOISE VS TREND   ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val testConditions = listOf(
            "Condition 1: Stationary + Partial Fill Noise (sigma_S=0.8L)" to VffCondition.STATIONARY_NOISE,
            "Condition 2: Odometer Typo (Single 300% Distance Spike at step 5)" to VffCondition.ODOMETER_TYPO,
            "Condition 3: Isolated Fuel Entry Error (Single 50% Fuel Drop at step 5)" to VffCondition.FUEL_TYPO,
            "Condition 4: Genuine Abrupt Step Change (35 -> 25 km/L at step 10)" to VffCondition.GENUINE_STEP
        )

        val testedLambdas = listOf(
            "Fixed lambda = 0.95" to 0.95,
            "Fixed lambda = 0.98" to 0.98,
            "Fixed lambda = 0.99" to 0.99,
            "Fixed lambda = 1.00" to 1.00
        )

        for ((condName, condType) in testConditions) {
            reportBuilder.appendLine("\n--- $condName ---")
            reportBuilder.appendLine("%-24s | %-14s | %-14s | %-20s".format(
                "Estimator Configuration", "Overall MAE", "Overall RMSE", "False Drop Rate %"
            ))
            reportBuilder.appendLine("-".repeat(78))

            for ((lamName, lamVal) in testedLambdas) {
                val rng = Random(4000L)
                var sumAbsErr = 0.0
                var sumSqErr = 0.0
                var totalObs = 0

                for (run in 0 until NUM_MC_RUNS) {
                    val (obs, trueThetas) = generateVffObservations(rng, condType, 20)
                    val engine = RecursiveLeastSquaresEngine(forgettingFactor = lamVal)
                    val states = engine.processSequence(obs)

                    for (i in obs.indices) {
                        val err = states[i].theta - trueThetas[i]
                        sumAbsErr += abs(err)
                        sumSqErr += err * err
                        totalObs++
                    }
                }

                val mae = sumAbsErr / totalObs
                val rmse = sqrt(sumSqErr / totalObs)
                reportBuilder.appendLine("%-24s | %-14.4f | %-14.4f | %-20s".format(
                    lamName, mae, rmse, "N/A (Fixed)"
                ))
            }

            // Test Dynamic Ad-hoc Adaptive Rule
            val rngAdhoc = Random(4000L)
            var sumAbsErrAdhoc = 0.0
            var sumSqErrAdhoc = 0.0
            var totalObsAdhoc = 0
            var dropCountAdhoc = 0

            for (run in 0 until NUM_MC_RUNS) {
                val (obs, trueThetas) = generateVffObservations(rngAdhoc, condType, 20)
                val (states, lambdasUsed) = processAdhocAdaptiveVff(obs)

                for (i in obs.indices) {
                    val err = states[i].theta - trueThetas[i]
                    sumAbsErrAdhoc += abs(err)
                    sumSqErrAdhoc += err * err
                    totalObsAdhoc++

                    if (lambdasUsed[i] <= 0.92) {
                        // Count as false drop if it's stationary, typo, or fuel error condition
                        if (condType != VffCondition.GENUINE_STEP || i < 9) {
                            dropCountAdhoc++
                        }
                    }
                }
            }

            val maeAdhoc = sumAbsErrAdhoc / totalObsAdhoc
            val rmseAdhoc = sqrt(sumSqErrAdhoc / totalObsAdhoc)
            val dropRate = dropCountAdhoc * 100.0 / totalObsAdhoc

            reportBuilder.appendLine("%-24s | %-14.4f | %-14.4f | %-20.2f".format(
                "Dynamic Ad-hoc Adaptive", maeAdhoc, rmseAdhoc, dropRate
            ))
        }
    }

    // -------------------------------------------------------------------------
    // 5. VALIDATE HYBRID ARCHITECTURE
    // -------------------------------------------------------------------------
    private fun validateHybridArchitecture() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("5. HYBRID ARCHITECTURE BENCHMARK & RELIABILITY CALIBRATION                 ")
        reportBuilder.appendLine("==========================================================================")

        val dynamicScenarios = listOf(
            "Condition A: Stationary Riding (theta = 35 km/L)" to DynamicPattern.STATIONARY,
            "Condition B: Gradual Change (35 -> 28 km/L over 20 refuels)" to DynamicPattern.GRADUAL_DECAY,
            "Condition C: Abrupt Step Change (40 -> 28 km/L at k=10)" to DynamicPattern.ABRUPT_STEP,
            "Condition D: Noisy Riding (Heavy Tank Partial Fills sigma_S=1.2L)" to DynamicPattern.NOISY
        )

        val candidateArchitectures = listOf(
            "1. Cumulative Baseline" to EstimatorArchitecture.CUMULATIVE,
            "2. Fixed-lambda RLS (lam=0.98)" to EstimatorArchitecture.FIXED_RLS,
            "3. Adaptive-lambda RLS" to EstimatorArchitecture.ADAPTIVE_RLS,
            "4. Hybrid (Cumulative + RLS Trend)" to EstimatorArchitecture.HYBRID
        )

        val tankCapacity = 10.0 // 10L tank

        for ((scenName, pattern) in dynamicScenarios) {
            reportBuilder.appendLine("\n--- $scenName ---")
            reportBuilder.appendLine("%-32s | %-10s %-10s | %-10s %-10s | %-10s".format(
                "Architecture", "FE MAE", "FE RMSE", "Dist MAE", "Dist RMSE", "Range MAE"
            ))
            reportBuilder.appendLine("-".repeat(95))

            for ((archName, archType) in candidateArchitectures) {
                val rng = Random(5000L + pattern.ordinal)
                var sumFeMae = 0.0
                var sumFeSqErr = 0.0

                var sumDistMae = 0.0
                var sumDistSqErr = 0.0

                var sumRangeMae = 0.0
                var totalTestPoints = 0

                for (run in 0 until NUM_MC_RUNS) {
                    val (obsAll, trueThetasAll) = generateDynamicObservationsWithPattern(rng, pattern, 20)
                    val nTrain = 5
                    val trainObs = obsAll.take(nTrain)
                    val testObs = obsAll.drop(nTrain)
                    val testTrueThetas = trueThetasAll.drop(nTrain)

                    // Evaluate estimate at end of training
                    val estResult = fitArchitecture(archType, trainObs)
                    val estTheta = estResult.predictedTheta

                    val trueThetaTrainEnd = trueThetasAll[nTrain - 1]
                    val feErr = estTheta - trueThetaTrainEnd

                    sumFeMae += abs(feErr)
                    sumFeSqErr += feErr * feErr

                    val trueRange = trueThetaTrainEnd * tankCapacity
                    val predRange = estTheta * tankCapacity
                    sumRangeMae += abs(predRange - trueRange)

                    for (i in testObs.indices) {
                        totalTestPoints++
                        val (yTest, xTest) = testObs[i]
                        val yPred = estTheta * xTest
                        val distErr = yPred - yTest
                        sumDistMae += abs(distErr)
                        sumDistSqErr += distErr * distErr
                    }
                }

                val feMae = sumFeMae / NUM_MC_RUNS
                val feRmse = sqrt(sumFeSqErr / NUM_MC_RUNS)
                val distMae = sumDistMae / totalTestPoints
                val distRmse = sqrt(sumDistSqErr / totalTestPoints)
                val rangeMae = sumRangeMae / NUM_MC_RUNS

                reportBuilder.appendLine("%-32s | %-10.4f %-10.4f | %-10.3f %-10.3f | %-10.3f".format(
                    archName, feMae, feRmse, distMae, distRmse, rangeMae
                ))
            }
        }
    }

    // -------------------------------------------------------------------------
    // 6. AUDIT EXISTING TEST REPORT
    // -------------------------------------------------------------------------
    private fun auditExistingTestReport() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("6. AUDIT OF EXISTING TEST REPORT: OUTLIER SENSITIVITY POSITION DISCREPANCY  ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val nTrain = 10
        val testedLambdas = listOf(0.95, 0.98, 0.99, 0.995)

        reportBuilder.appendLine("Investigating Outlier Sensitivity across Outlier Positions (Sample 1 vs Sample 5 vs Sample 10):")
        reportBuilder.appendLine("%-15s | %-18s | %-18s | %-18s".format(
            "Lambda", "Outlier at Sample 1", "Outlier at Sample 5", "Outlier at Sample 10"
        ))
        reportBuilder.appendLine("-".repeat(75))

        for (lam in testedLambdas) {
            val sensPos1 = measureOutlierSensitivityAtPosition(lam, trueTheta, nTrain, 0)
            val sensPos5 = measureOutlierSensitivityAtPosition(lam, trueTheta, nTrain, 4)
            val sensPos10 = measureOutlierSensitivityAtPosition(lam, trueTheta, nTrain, 9)

            reportBuilder.appendLine("%-15.3f | %-18.4f | %-18.4f | %-18.4f".format(
                lam, sensPos1, sensPos5, sensPos10
            ))
        }
    }

    // -------------------------------------------------------------------------
    // HELPER FUNCTIONS & TYPES
    // -------------------------------------------------------------------------
    private enum class InventoryType { INDEPENDENT, RANDOM_WALK, MEAN_REVERTING }
    private enum class NoiseType { GAUSSIAN, STUDENT_T }
    private enum class VffCondition { STATIONARY_NOISE, ODOMETER_TYPO, FUEL_TYPO, GENUINE_STEP }
    private enum class DynamicPattern { STATIONARY, GRADUAL_DECAY, ABRUPT_STEP, NOISY }
    private enum class EstimatorArchitecture { CUMULATIVE, FIXED_RLS, ADAPTIVE_RLS, HYBRID }

    private data class ArchitectureResult(val predictedTheta: Double, val stableTheta: Double)

    private fun generateObservations(
        rng: Random,
        trueTheta: Double,
        count: Int,
        inventoryType: InventoryType,
        sigmaS: Double,
        sigmaNoise: Double,
        phi: Double = 0.7,
        noiseType: NoiseType = NoiseType.GAUSSIAN
    ): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var prevS = 0.0

        for (k in 0 until count) {
            val trueFuelConsumed = 2.0 + rng.nextDouble() * 5.0
            val currentS = when (inventoryType) {
                InventoryType.INDEPENDENT -> sampleNoise(rng, sigmaS, noiseType)
                InventoryType.RANDOM_WALK -> prevS + sampleNoise(rng, sigmaS, noiseType)
                InventoryType.MEAN_REVERTING -> phi * prevS + sampleNoise(rng, sigmaS, noiseType)
            }

            val fuelAdded = max(0.2, trueFuelConsumed + currentS - prevS)
            prevS = currentS

            val distNoise = sampleNoise(rng, sigmaNoise, noiseType)
            val distanceKm = max(1.0, trueTheta * trueFuelConsumed + distNoise)

            result.add(Pair(distanceKm, fuelAdded))
        }

        return result
    }

    private fun sampleNoise(rng: Random, scale: Double, noiseType: NoiseType): Double {
        return when (noiseType) {
            NoiseType.GAUSSIAN -> rng.nextGaussian() * scale
            NoiseType.STUDENT_T -> {
                val z = rng.nextGaussian()
                val x1 = rng.nextGaussian()
                val x2 = rng.nextGaussian()
                val x3 = rng.nextGaussian()
                val chiSq = x1 * x1 + x2 * x2 + x3 * x3
                val t = z / sqrt(chiSq / 3.0)
                t * (scale / sqrt(3.0))
            }
        }
    }

    private fun computeSandwichMoe95(state: RlsState, obs: List<Pair<Double, Double>>): Double {
        if (state.sampleCount < 2 || state.effectiveSampleSize <= 1.0) return 0.0
        val theta = state.theta

        var numSum = 0.0
        var denSum = 0.0

        for ((dist, fuel) in obs) {
            val pred = theta * fuel
            val resid = dist - pred
            numSum += (fuel * resid).pow(2)
            denSum += fuel * fuel
        }

        val robustVar = if (denSum > 1e-12) numSum / (denSum * denSum) else state.covariance
        val robustSe = sqrt(max(0.0, robustVar))

        val dof = max(1, (state.effectiveSampleSize - 1.0).toInt())
        val tCrit = RlsState.getTCriticalValue95(dof)
        return tCrit * robustSe
    }

    private fun computeHacLag1Moe95(state: RlsState, obs: List<Pair<Double, Double>>): Double {
        if (state.sampleCount < 2 || state.effectiveSampleSize <= 1.0) return 0.0
        val theta = state.theta
        val N = obs.size

        var denSum = 0.0
        val terms = mutableListOf<Double>()

        for ((dist, fuel) in obs) {
            val pred = theta * fuel
            val resid = dist - pred
            terms.add(fuel * resid)
            denSum += fuel * fuel
        }

        var numSum = terms.sumOf { it * it }

        // Add lag-1 cross-covariance term with Bartlett weight w_1 = 1 - 1/2 = 0.5
        for (i in 0 until N - 1) {
            numSum += 2.0 * 0.5 * terms[i] * terms[i + 1]
        }

        val hacVar = if (denSum > 1e-12) max(0.0, numSum / (denSum * denSum)) else state.covariance
        val hacSe = sqrt(hacVar)

        val dof = max(1, (state.effectiveSampleSize - 1.0).toInt())
        val tCrit = RlsState.getTCriticalValue95(dof)
        return tCrit * hacSe
    }

    private fun computeKishNeff(N: Int, lambda: Double): Double {
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until N) {
            val w = lambda.pow(N - 1 - i)
            s1 += w
            s2 += w * w
        }
        return if (s2 > 1e-12) (s1 * s1) / s2 else N.toDouble()
    }

    private fun generateVffObservations(
        rng: Random,
        condition: VffCondition,
        count: Int
    ): Pair<List<Pair<Double, Double>>, List<Double>> {
        val obs = mutableListOf<Pair<Double, Double>>()
        val trueThetas = mutableListOf<Double>()
        var prevS = 0.0

        for (k in 0 until count) {
            val thetaK = if (condition == VffCondition.GENUINE_STEP && k >= 10) 25.0 else 35.0
            trueThetas.add(thetaK)

            val trueFuel = 4.0
            val tankDev = rng.nextGaussian() * 0.8
            var fuelAdded = max(0.5, trueFuel + tankDev - prevS)
            prevS = tankDev

            var distNoise = rng.nextGaussian() * 3.0
            var dist = max(1.0, thetaK * trueFuel + distNoise)

            // Inject typos / errors
            if (condition == VffCondition.ODOMETER_TYPO && k == 4) {
                dist *= 3.0 // 300% distance typo spike
            }
            if (condition == VffCondition.FUEL_TYPO && k == 4) {
                fuelAdded *= 0.5 // 50% fuel entry drop
            }

            obs.add(Pair(dist, fuelAdded))
        }

        return Pair(obs, trueThetas)
    }

    private fun processAdhocAdaptiveVff(obs: List<Pair<Double, Double>>): Pair<List<RlsState>, List<Double>> {
        val states = mutableListOf<RlsState>()
        val lambdasUsed = mutableListOf<Double>()
        var theta = 0.0
        var P = 100.0
        var count = 0
        var rss = 0.0
        var neff = 0.0

        for ((y, x) in obs) {
            val empiricalRatio = y / x
            if (count == 0 || theta <= 0.0) {
                theta = empiricalRatio
                P = P / (0.98 + x * x * P)
                count = 1
                neff = 1.0
                rss = 0.0
                states.add(RlsState(theta, P, count, rss, neff))
                lambdasUsed.add(0.98)
                continue
            }

            val pred = theta * x
            val err = y - pred

            val varEst = if (neff > 1.0) rss / (neff - 1.0) else 100.0
            val normalizedErrSq = (err * err) / max(1.0, varEst)
            val lambdaK = min(0.995, max(0.90, 1.0 - (0.05 * normalizedErrSq)))
            lambdasUsed.add(lambdaK)

            val denom = lambdaK + x * x * P
            val gain = (P * x) / denom

            theta += gain * err
            P = (1.0 / lambdaK) * (1.0 - gain * x) * P

            if (theta <= 0.0) theta = 35.0
            if (P < 1e-6) P = 1e-3

            count++
            neff = lambdaK * neff + 1.0
            val postGain = max(0.0, 1.0 - gain * x)
            rss = lambdaK * rss + postGain * err * err

            states.add(RlsState(theta, P, count, rss, neff))
        }

        return Pair(states, lambdasUsed)
    }

    private fun generateDynamicObservationsWithPattern(
        rng: Random,
        pattern: DynamicPattern,
        count: Int
    ): Pair<List<Pair<Double, Double>>, List<Double>> {
        val obs = mutableListOf<Pair<Double, Double>>()
        val trueThetas = mutableListOf<Double>()
        var prevS = 0.0

        for (k in 0 until count) {
            val thetaK = when (pattern) {
                DynamicPattern.STATIONARY -> 35.0
                DynamicPattern.GRADUAL_DECAY -> 35.0 - (7.0 * k / (count - 1))
                DynamicPattern.ABRUPT_STEP -> if (k < 10) 40.0 else 28.0
                DynamicPattern.NOISY -> 35.0
            }
            trueThetas.add(thetaK)

            val sigS = if (pattern == DynamicPattern.NOISY) 1.2 else 0.6
            val sigD = if (pattern == DynamicPattern.NOISY) 5.0 else 3.0

            val trueFuel = 4.0
            val currentS = rng.nextGaussian() * sigS
            val fuelAdded = max(0.4, trueFuel + currentS - prevS)
            prevS = currentS

            val distNoise = rng.nextGaussian() * sigD
            val dist = max(1.0, thetaK * trueFuel + distNoise)

            obs.add(Pair(dist, fuelAdded))
        }

        return Pair(obs, trueThetas)
    }

    private fun fitArchitecture(type: EstimatorArchitecture, obs: List<Pair<Double, Double>>): ArchitectureResult {
        val sumD = obs.sumOf { it.first }
        val sumF = obs.sumOf { it.second }
        val cumulTheta = if (sumF > 0.0) sumD / sumF else 35.0

        return when (type) {
            EstimatorArchitecture.CUMULATIVE -> {
                ArchitectureResult(predictedTheta = cumulTheta, stableTheta = cumulTheta)
            }
            EstimatorArchitecture.FIXED_RLS -> {
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
                val rlsTheta = engine.processSequence(obs).last().theta
                ArchitectureResult(predictedTheta = rlsTheta, stableTheta = cumulTheta)
            }
            EstimatorArchitecture.ADAPTIVE_RLS -> {
                val (states, _) = processAdhocAdaptiveVff(obs)
                val adaptiveTheta = states.last().theta
                ArchitectureResult(predictedTheta = adaptiveTheta, stableTheta = cumulTheta)
            }
            EstimatorArchitecture.HYBRID -> {
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
                val rlsTheta = engine.processSequence(obs).last().theta
                // Hybrid uses Cumulative baseline as stable economy estimate and RLS as dynamic trend estimate
                ArchitectureResult(predictedTheta = cumulTheta, stableTheta = rlsTheta)
            }
        }
    }

    private fun measureOutlierSensitivityAtPosition(
        lambda: Double,
        trueTheta: Double,
        nTrain: Int,
        outlierIndex: Int
    ): Double {
        val rng = Random(6000L)
        var sumFeMaeClean = 0.0
        var sumFeMaeOutlier = 0.0

        for (run in 0 until NUM_MC_RUNS) {
            val obsAll = generateObservations(rng, trueTheta, nTrain, InventoryType.INDEPENDENT, 0.6, 3.0)

            val engine = RecursiveLeastSquaresEngine(forgettingFactor = lambda)
            val thetaClean = engine.processSequence(obsAll).last().theta

            val outlierObs = obsAll.toMutableList().apply {
                this[outlierIndex] = Pair(this[outlierIndex].first * 3.0, this[outlierIndex].second)
            }
            val thetaOutlier = engine.processSequence(outlierObs).last().theta

            sumFeMaeClean += abs(thetaClean - trueTheta)
            sumFeMaeOutlier += abs(thetaOutlier - trueTheta)
        }

        return (sumFeMaeOutlier / NUM_MC_RUNS) - (sumFeMaeClean / NUM_MC_RUNS)
    }
}
