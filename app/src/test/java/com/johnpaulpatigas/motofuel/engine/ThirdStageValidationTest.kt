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
 * MotoFuel Third-Stage Validation Test Suite: Predictive Accuracy and Statistical Integrity.
 * Executes extensive Monte Carlo simulations (10,000 runs per setup) to rigorously evaluate
 * RLS vs benchmark estimators, autocorrelation assumptions, confidence interval coverage,
 * covariance floor sensitivity, prior bias, and forgetting factor performance.
 */
class ThirdStageValidationTest {

    private val reportBuilder = StringBuilder()

    companion object {
        const val NUM_MC_RUNS = 10000
    }

    @Test
    fun `run full third stage validation`() {
        reportBuilder.appendLine("==========================================================================")
        reportBuilder.appendLine("    MOTOFUEL THIRD-STAGE VALIDATION: PREDICTIVE ACCURACY & INTEGRITY     ")
        reportBuilder.appendLine("==========================================================================")

        runAutocorrelationInvestigation()
        runConfidenceIntervalValidation()
        runCovarianceFloorSensitivity()
        runPredictivePerformanceBenchmark()
        runPriorSensitivityTest()
        runForgettingFactorEvaluation()

        val reportText = reportBuilder.toString()
        println(reportText)

        // Save report to system temporary directory
        File(System.getProperty("java.io.tmpdir"), "third_stage_validation_results.txt").writeText(reportText)
    }

    // -------------------------------------------------------------------------
    // 1. REASSESS AUTOCORRELATION ASSUMPTION
    // -------------------------------------------------------------------------
    private fun runAutocorrelationInvestigation() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("1. REASSESSING AUTOCORRELATION ASSUMPTIONS IN PARTIAL-REFUELING DYNAMICS  ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val sequenceLength = 20

        val inventoryModels = listOf(
            "Independent i.i.d. (sigma_S = 0.8L)" to InventoryType.INDEPENDENT,
            "Persistent / Random Walk (sigma_eta = 0.5L)" to InventoryType.RANDOM_WALK,
            "Mean-Reverting AR(1) (phi = 0.7, sigma_eta = 0.5L)" to InventoryType.MEAN_REVERTING
        )

        for ((modelName, modelType) in inventoryModels) {
            val rng = Random(101L)
            var sumLag1AutoCorrFuel = 0.0
            var sumLag1AutoCorrResid = 0.0
            var sumRlsVar = 0.0
            var sumCumulVar = 0.0

            val thetaEstRlsList = mutableListOf<Double>()
            val thetaEstCumulList = mutableListOf<Double>()

            for (run in 0 until NUM_MC_RUNS) {
                val obs = generateObservations(rng, trueTheta, sequenceLength, modelType, 0.8, 0.5, 0.7)

                // Measure raw fuel regressor errors (delta S_k)
                val fuelErrors = obs.map { (y, x) -> x - (y / trueTheta) }
                val lag1Fuel = computeLag1Autocorrelation(fuelErrors)
                sumLag1AutoCorrFuel += lag1Fuel

                // Fit RLS
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
                val states = engine.processSequence(obs)
                val finalState = states.last()
                thetaEstRlsList.add(finalState.theta)

                // Fit Cumulative
                val totalDist = obs.sumOf { it.first }
                val totalFuel = obs.sumOf { it.second }
                val cumulTheta = if (totalFuel > 0.0) totalDist / totalFuel else 0.0
                thetaEstCumulList.add(cumulTheta)

                // RLS residual lag-1 autocorrelation
                val rlsResids = obs.map { (y, x) -> y - finalState.theta * x }
                val lag1Resid = computeLag1Autocorrelation(rlsResids)
                sumLag1AutoCorrResid += lag1Resid
            }

            val rlsMeanTheta = thetaEstRlsList.average()
            val rlsVar = thetaEstRlsList.map { (it - rlsMeanTheta).pow(2) }.average()

            val cumulMeanTheta = thetaEstCumulList.average()
            val cumulVar = thetaEstCumulList.map { (it - cumulMeanTheta).pow(2) }.average()

            reportBuilder.appendLine("\nModel: $modelName")
            reportBuilder.appendLine("  Average Fuel Error Lag-1 Autocorrelation (rho_1_fuel)  : %6.4f".format(sumLag1AutoCorrFuel / NUM_MC_RUNS))
            reportBuilder.appendLine("  Average RLS Residual Lag-1 Autocorrelation (rho_1_res)  : %6.4f".format(sumLag1AutoCorrResid / NUM_MC_RUNS))
            reportBuilder.appendLine("  Empirical Variance of RLS Estimator Var(theta_RLS)     : %8.6f (StdDev = %.4f km/L)".format(rlsVar, sqrt(rlsVar)))
            reportBuilder.appendLine("  Empirical Variance of Cumulative Estimator Var(theta_Cum): %8.6f (StdDev = %.4f km/L)".format(cumulVar, sqrt(cumulVar)))
            reportBuilder.appendLine("  Variance Ratio Var(RLS) / Var(Cumulative)             : %6.3f".format(rlsVar / max(1e-9, cumulVar)))
        }
    }

    // -------------------------------------------------------------------------
    // 2. EXPAND CONFIDENCE INTERVAL VALIDATION
    // -------------------------------------------------------------------------
    private fun runConfidenceIntervalValidation() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("2. EXPANDED CONFIDENCE INTERVAL COVERAGE VALIDATION (10,000 MONTE CARLO)  ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val sampleSizes = listOf(2, 3, 5, 10, 20, 50)

        val testScenarios = listOf(
            "Scenario A: Gaussian Noise + Independent Partial Fills (sigma_S = 0.6L)" to
                    Pair(InventoryType.INDEPENDENT, NoiseType.GAUSSIAN),
            "Scenario B: Heavy-Tailed Student-t Noise (df=3) + Partial Fills" to
                    Pair(InventoryType.INDEPENDENT, NoiseType.STUDENT_T),
            "Scenario C: Persistent Random Walk Tank Inventory Noise" to
                    Pair(InventoryType.RANDOM_WALK, NoiseType.GAUSSIAN),
            "Scenario D: Mean-Reverting Tank Inventory Noise (phi = 0.7)" to
                    Pair(InventoryType.MEAN_REVERTING, NoiseType.GAUSSIAN)
        )

        for ((scenName, scenPair) in testScenarios) {
            val (invType, noiseType) = scenPair
            reportBuilder.appendLine("\n--- $scenName ---")
            reportBuilder.appendLine("%-8s | %-18s | %-18s | %-18s".format(
                "Obs (N)", "Existing RLS CI %", "Robust Sandwich CI %", "Empirical MC StdDev"
            ))
            reportBuilder.appendLine("-".repeat(70))

            for (N in sampleSizes) {
                val rng = Random((2026 + N).toLong())
                var countCoveredExisting = 0
                var countCoveredRobust = 0
                val thetaEstimates = mutableListOf<Double>()

                for (run in 0 until NUM_MC_RUNS) {
                    val obs = generateObservations(rng, trueTheta, N, invType, 0.6, 0.4, 0.7, noiseType)
                    val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98)
                    val states = engine.processSequence(obs)
                    val finalState = states.last()
                    val estTheta = finalState.theta
                    thetaEstimates.add(estTheta)

                    // 1. Existing RLS 95% CI
                    val moeExisting = finalState.marginOfError95
                    if (abs(estTheta - trueTheta) <= moeExisting) {
                        countCoveredExisting++
                    }

                    // 2. Robust Sandwich-Adjusted CI
                    val moeRobust = computeRobustMoe95(finalState, obs)
                    if (abs(estTheta - trueTheta) <= moeRobust) {
                        countCoveredRobust++
                    }
                }

                val mcMean = thetaEstimates.average()
                val mcStdDev = sqrt(thetaEstimates.map { (it - mcMean).pow(2) }.average())
                val coverageExisting = countCoveredExisting * 100.0 / NUM_MC_RUNS
                val coverageRobust = countCoveredRobust * 100.0 / NUM_MC_RUNS

                reportBuilder.appendLine("%-8d | %-18.2f | %-18.2f | %-18.4f".format(
                    N, coverageExisting, coverageRobust, mcStdDev
                ))
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. INVESTIGATE COVARIANCE FLOOR SENSITIVITY
    // -------------------------------------------------------------------------
    private fun runCovarianceFloorSensitivity() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("3. COVARIANCE FLOOR SENSITIVITY ANALYSIS (10,000 MONTE CARLO RUNS)       ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val floors = listOf(0.5, 1.0, 2.5, 5.0, 10.0)
        val sampleSteps = listOf(1, 2, 3, 5, 10, 20)

        reportBuilder.appendLine("%-12s | %-8s | %-16s | %-18s | %-16s".format(
            "P_floor", "Step (N)", "Theta RMSE (km/L)", "Avg 95% MOE (km/L)", "95% Coverage %"
        ))
        reportBuilder.appendLine("-".repeat(80))

        for (floor in floors) {
            val stepSqErr = mutableMapOf<Int, Double>().apply { sampleSteps.forEach { put(it, 0.0) } }
            val stepMoe = mutableMapOf<Int, Double>().apply { sampleSteps.forEach { put(it, 0.0) } }
            val stepCoverage = mutableMapOf<Int, Int>().apply { sampleSteps.forEach { put(it, 0) } }

            val rng = Random(3030L)
            for (run in 0 until NUM_MC_RUNS) {
                val obs = generateObservations(rng, trueTheta, 20, InventoryType.INDEPENDENT, 0.6, 0.4)

                // Process with custom floor engine
                var state = RlsState()
                for (k in 1..20) {
                    val (dist, fuel) = obs[k - 1]
                    state = updateWithCovarianceFloor(state, dist, fuel, floor)

                    if (k in sampleSteps) {
                        val est = state.theta
                        val err = est - trueTheta
                        stepSqErr[k] = stepSqErr[k]!! + err * err
                        val moe = state.marginOfError95
                        stepMoe[k] = stepMoe[k]!! + moe
                        if (abs(err) <= moe) {
                            stepCoverage[k] = stepCoverage[k]!! + 1
                        }
                    }
                }
            }

            for (k in sampleSteps) {
                val rmse = sqrt(stepSqErr[k]!! / NUM_MC_RUNS)
                val avgMoe = stepMoe[k]!! / NUM_MC_RUNS
                val cov = stepCoverage[k]!! * 100.0 / NUM_MC_RUNS
                reportBuilder.appendLine("%-12.1f | %-8d | %-16.4f | %-18.4f | %-16.2f".format(
                    floor, k, rmse, avgMoe, cov
                ))
            }
            reportBuilder.appendLine("-".repeat(80))
        }
    }

    // -------------------------------------------------------------------------
    // 4. BENCHMARK ACTUAL PREDICTIVE PERFORMANCE
    // -------------------------------------------------------------------------
    private fun runPredictivePerformanceBenchmark() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("4. CHRONOLOGICAL OUT-OF-SAMPLE BENCHMARK OF ESTIMATORS (10,000 RUNS)       ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val trainSizes = listOf(3, 5, 10)
        val testSize = 5
        val tankCapacity = 10.0 // 10 Liters

        for (nTrain in trainSizes) {
            reportBuilder.appendLine("\n--- Benchmark Configuration: Training N = $nTrain, Testing M = $testSize ---")
            reportBuilder.appendLine("%-28s | %-11s | %-11s | %-11s | %-11s | %-11s | %-11s".format(
                "Estimator", "FE MAE(kmL)", "FE RMSE(kmL)", "Dist MAE(km)", "Dist RMSE(km)", "Rng MAE(km)", "Outlier Sens"
            ))
            reportBuilder.appendLine("-".repeat(105))

            val estimators = listOf(
                "A. RLS (lambda=0.98, Prior=35)" to EstimatorType.RLS_LAMBDA_098_PRIOR,
                "B. Cumulative Baseline" to EstimatorType.CUMULATIVE,
                "C. RLS (lambda=0.95)" to EstimatorType.RLS_LAMBDA_095,
                "D. RLS (lambda=0.99)" to EstimatorType.RLS_LAMBDA_099,
                "E. RLS (lambda=0.995)" to EstimatorType.RLS_LAMBDA_0995,
                "F. Robust Trimmed Cumulative" to EstimatorType.ROBUST_CUMULATIVE
            )

            for ((estName, estType) in estimators) {
                val rng = Random((4040 + nTrain).toLong())
                var sumFeMae = 0.0
                var sumFeSqErr = 0.0

                var sumDistMae = 0.0
                var sumDistSqErr = 0.0

                var sumRangeMae = 0.0
                var totalTestPoints = 0

                var sumFeMaeClean = 0.0
                var sumFeMaeOutlier = 0.0

                for (run in 0 until NUM_MC_RUNS) {
                    val obsAll = generateObservations(rng, trueTheta, nTrain + testSize, InventoryType.INDEPENDENT, 0.6, 0.4)
                    val trainObs = obsAll.take(nTrain)
                    val testObs = obsAll.drop(nTrain)

                    // Train clean
                    val thetaClean = fitEstimator(estType, trainObs, targetPrior = 35.0)

                    // Train with 1 outlier in training set (e.g., 300% distance spike due to typo)
                    val outlierTrainObs = trainObs.toMutableList().apply {
                        this[0] = Pair(this[0].first * 3.0, this[0].second)
                    }
                    val thetaOutlier = fitEstimator(estType, outlierTrainObs, targetPrior = 35.0)

                    val feErrClean = abs(thetaClean - trueTheta)
                    val feErrOutlier = abs(thetaOutlier - trueTheta)

                    sumFeMaeClean += feErrClean
                    sumFeMaeOutlier += feErrOutlier

                    sumFeMae += feErrClean
                    sumFeSqErr += (thetaClean - trueTheta).pow(2)

                    // Test set distance prediction & range prediction
                    val trueRange = trueTheta * tankCapacity
                    val predRange = thetaClean * tankCapacity
                    sumRangeMae += abs(predRange - trueRange)

                    for ((yTest, xTest) in testObs) {
                        totalTestPoints++
                        val yPred = thetaClean * xTest
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
                val outlierSensitivity = (sumFeMaeOutlier / NUM_MC_RUNS) - (sumFeMaeClean / NUM_MC_RUNS)

                reportBuilder.appendLine("%-28s | %-11.4f | %-11.4f | %-11.3f | %-11.3f | %-11.3f | %-11.4f".format(
                    estName, feMae, feRmse, distMae, distRmse, rangeMae, outlierSensitivity
                ))
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. TEST PRIOR SENSITIVITY
    // -------------------------------------------------------------------------
    private fun runPriorSensitivityTest() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("5. PRIOR SENSITIVITY & BIAS ANALYSIS (TRUE THETA = 35.0 KM/L)             ")
        reportBuilder.appendLine("==========================================================================")

        val trueTheta = 35.0
        val priors = listOf(
            "A. Generic / No Prior (theta0=0)" to Pair(0.0, 25.0),
            "B. Accurate Target Prior (theta0=35)" to Pair(35.0, 25.0),
            "C. Optimistic Target Prior (theta0=45)" to Pair(45.0, 25.0),
            "D. Pessimistic Target Prior (theta0=25)" to Pair(25.0, 25.0),
            "E. Extreme Optimistic Prior (theta0=60)" to Pair(60.0, 25.0),
            "F. Optimistic Strong Cov (theta0=45, P0=5)" to Pair(45.0, 5.0),
            "G. Optimistic Weak Cov (theta0=45, P0=100)" to Pair(45.0, 100.0)
        )

        val evalSteps = listOf(1, 2, 3, 5, 10, 20)

        for ((priorName, priorConfig) in priors) {
            val (pTheta, pCov) = priorConfig
            reportBuilder.appendLine("\nPrior Setup: $priorName")
            reportBuilder.appendLine("%-10s | %-16s | %-16s | %-18s".format(
                "Step (N)", "Avg Theta (km/L)", "Bias (km/L)", "RMSE (km/L)"
            ))
            reportBuilder.appendLine("-".repeat(68))

            val stepThetaSum = mutableMapOf<Int, Double>().apply { evalSteps.forEach { put(it, 0.0) } }
            val stepSqErrSum = mutableMapOf<Int, Double>().apply { evalSteps.forEach { put(it, 0.0) } }

            val rng = Random(5050L)
            for (run in 0 until NUM_MC_RUNS) {
                val obs = generateObservations(rng, trueTheta, 20, InventoryType.INDEPENDENT, 0.6, 0.4)

                val states = if (pTheta > 0.0) {
                    processSequenceWithExplicitPrior(obs, pTheta, pCov)
                } else {
                    RecursiveLeastSquaresEngine(forgettingFactor = 0.98).processSequence(obs)
                }

                for (k in evalSteps) {
                    val est = states[k - 1].theta
                    stepThetaSum[k] = stepThetaSum[k]!! + est
                    stepSqErrSum[k] = stepSqErrSum[k]!! + (est - trueTheta).pow(2)
                }
            }

            for (k in evalSteps) {
                val avgTheta = stepThetaSum[k]!! / NUM_MC_RUNS
                val bias = avgTheta - trueTheta
                val rmse = sqrt(stepSqErrSum[k]!! / NUM_MC_RUNS)
                reportBuilder.appendLine("%-10d | %-16.4f | %-16.4f | %-18.4f".format(
                    k, avgTheta, bias, rmse
                ))
            }
        }
    }

    // -------------------------------------------------------------------------
    // 6. EVALUATE FORGETTING FACTOR SELECTION
    // -------------------------------------------------------------------------
    private fun runForgettingFactorEvaluation() {
        reportBuilder.appendLine("\n==========================================================================")
        reportBuilder.appendLine("6. FORGETTING FACTOR & DYNAMIC SHIFT TRACKING EVALUATION                   ")
        reportBuilder.appendLine("==========================================================================")

        val dynamicScenarios = listOf(
            "Scenario 1: Stationary Efficiency (theta = 35.0 km/L constant)" to DynamicPattern.STATIONARY,
            "Scenario 2: Gradual Efficiency Decay (35 -> 28 km/L over 20 refuels)" to DynamicPattern.GRADUAL_DECAY,
            "Scenario 3: Abrupt Step Change (40 km/L highway -> 28 km/L city at k=10)" to DynamicPattern.ABRUPT_STEP,
            "Scenario 4: Alternating Weekend/Weekday Driving (38 km/L vs 28 km/L)" to DynamicPattern.ALTERNATING
        )

        val candidateLambdas = listOf(0.95, 0.98, 0.99, 0.995, 1.0)

        for ((scenName, pattern) in dynamicScenarios) {
            reportBuilder.appendLine("\n--- $scenName ---")
            reportBuilder.appendLine("%-22s | %-16s | %-16s | %-18s".format(
                "Lambda / Estimator", "Overall MAE(km/L)", "Overall RMSE(km/L)", "Tracking Lag / CV"
            ))
            reportBuilder.appendLine("-".repeat(78))

            // Test fixed lambdas
            for (lam in candidateLambdas) {
                val rng = Random(6060L)
                var sumAbsErr = 0.0
                var sumSqErr = 0.0
                var totalObsCount = 0

                for (run in 0 until NUM_MC_RUNS) {
                    val (obs, trueThetas) = generateDynamicObservations(rng, pattern, 20)
                    val engine = RecursiveLeastSquaresEngine(forgettingFactor = lam)
                    val states = engine.processSequence(obs)

                    for (i in obs.indices) {
                        val pred = states[i].theta
                        val truth = trueThetas[i]
                        val err = pred - truth
                        sumAbsErr += abs(err)
                        sumSqErr += err * err
                        totalObsCount++
                    }
                }

                val mae = sumAbsErr / totalObsCount
                val rmse = sqrt(sumSqErr / totalObsCount)
                reportBuilder.appendLine("%-22s | %-16.4f | %-16.4f | %-18.4f".format(
                    "RLS lambda = %.3f".format(lam), mae, rmse, rmse / mae
                ))
            }

            // Test Adaptive Forgetting Factor (Fortescue VFF)
            val rngVff = Random(6060L)
            var sumAbsErrVff = 0.0
            var sumSqErrVff = 0.0
            var totalObsCountVff = 0

            for (run in 0 until NUM_MC_RUNS) {
                val (obs, trueThetas) = generateDynamicObservations(rngVff, pattern, 20)
                val states = processAdaptiveForgetting(obs)

                for (i in obs.indices) {
                    val pred = states[i].theta
                    val truth = trueThetas[i]
                    val err = pred - truth
                    sumAbsErrVff += abs(err)
                    sumSqErrVff += err * err
                    totalObsCountVff++
                }
            }

            val maeVff = sumAbsErrVff / totalObsCountVff
            val rmseVff = sqrt(sumSqErrVff / totalObsCountVff)
            reportBuilder.appendLine("%-22s | %-16.4f | %-16.4f | %-18.4f".format(
                "Adaptive VFF RLS", maeVff, rmseVff, rmseVff / maeVff
            ))
        }
    }

    // -------------------------------------------------------------------------
    // HELPER SIMULATION UTILITIES
    // -------------------------------------------------------------------------
    private enum class InventoryType { INDEPENDENT, RANDOM_WALK, MEAN_REVERTING }
    private enum class NoiseType { GAUSSIAN, STUDENT_T }
    private enum class EstimatorType { RLS_LAMBDA_098_PRIOR, CUMULATIVE, RLS_LAMBDA_095, RLS_LAMBDA_099, RLS_LAMBDA_0995, ROBUST_CUMULATIVE }
    private enum class DynamicPattern { STATIONARY, GRADUAL_DECAY, ABRUPT_STEP, ALTERNATING }

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
            val trueFuelConsumed = 2.0 + rng.nextDouble() * 5.0 // 2L - 7L
            val currentS = when (inventoryType) {
                InventoryType.INDEPENDENT -> sampleNoise(rng, sigmaS, noiseType)
                InventoryType.RANDOM_WALK -> prevS + sampleNoise(rng, sigmaS, noiseType)
                InventoryType.MEAN_REVERTING -> phi * prevS + sampleNoise(rng, sigmaS, noiseType)
            }

            val fuelAdded = max(0.3, trueFuelConsumed + currentS - prevS)
            prevS = currentS

            val distNoise = sampleNoise(rng, sigmaNoise * trueTheta, noiseType)
            val distanceKm = max(5.0, trueTheta * trueFuelConsumed + distNoise)

            result.add(Pair(distanceKm, fuelAdded))
        }

        return result
    }

    private fun generateDynamicObservations(
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
                DynamicPattern.GRADUAL_DECAY -> 35.0 - (7.0 * k / (count - 1)) // 35 -> 28
                DynamicPattern.ABRUPT_STEP -> if (k < 10) 40.0 else 28.0
                DynamicPattern.ALTERNATING -> if (k % 2 == 0) 38.0 else 28.0
            }
            trueThetas.add(thetaK)

            val trueFuel = 3.0 + rng.nextDouble() * 4.0
            val currentS = rng.nextGaussian() * 0.6
            val fuelAdded = max(0.4, trueFuel + currentS - prevS)
            prevS = currentS

            val distNoise = rng.nextGaussian() * 3.0
            val dist = max(5.0, thetaK * trueFuel + distNoise)

            obs.add(Pair(dist, fuelAdded))
        }

        return Pair(obs, trueThetas)
    }

    private fun sampleNoise(rng: Random, scale: Double, noiseType: NoiseType): Double {
        return when (noiseType) {
            NoiseType.GAUSSIAN -> rng.nextGaussian() * scale
            NoiseType.STUDENT_T -> {
                // Student-t with df=3 generated via Gaussian / sqrt(ChiSq/3)
                val z = rng.nextGaussian()
                val x1 = rng.nextGaussian()
                val x2 = rng.nextGaussian()
                val x3 = rng.nextGaussian()
                val chiSq = x1 * x1 + x2 * x2 + x3 * x3
                val t = z / sqrt(chiSq / 3.0)
                t * (scale / sqrt(3.0 / 1.0)) // scaled
            }
        }
    }

    private fun computeLag1Autocorrelation(series: List<Double>): Double {
        if (series.size < 2) return 0.0
        val mean = series.average()
        var num = 0.0
        var den = 0.0

        for (i in series.indices) {
            val dev = series[i] - mean
            den += dev * dev
            if (i < series.size - 1) {
                val devNext = series[i + 1] - mean
                num += dev * devNext
            }
        }
        return if (den > 1e-12) num / den else 0.0
    }

    private fun updateWithCovarianceFloor(state: RlsState, distanceKm: Double, litersAdded: Double, floor: Double): RlsState {
        val x = litersAdded
        val y = distanceKm
        val empiricalRatio = y / x

        if (state.sampleCount == 0 || state.theta <= 0.0) {
            val rawP = state.covariance / (0.98 + x * x * state.covariance)
            val initialP = if (rawP.isNaN() || rawP.isInfinite()) 10.0 else max(floor, rawP)
            return RlsState(theta = empiricalRatio, covariance = initialP, sampleCount = 1, residualSumOfSquares = 0.0, effectiveSampleSize = 1.0)
        }

        val priorTheta = state.theta
        val priorP = state.covariance
        val priorNeff = state.effectiveSampleSize
        val priorRss = state.residualSumOfSquares

        val prediction = priorTheta * x
        val rawError = y - prediction

        val priorVarianceEstimate = if (priorNeff > 1.0) priorRss / (priorNeff - 1.0) else 100.0
        val predVariance = max(1e-6, priorVarianceEstimate * (1.0 + x * x * priorP))
        val predStdDev = sqrt(predVariance)
        val standardizedResidual = if (predStdDev > 0.0) abs(rawError) / predStdDev else 0.0

        val errorForTheta = if (standardizedResidual > 3.0 && state.sampleCount >= 2) {
            rawError * (3.0 / standardizedResidual)
        } else rawError

        val errorForRss = if (standardizedResidual > 5.0 && state.sampleCount >= 2) {
            rawError * (5.0 / standardizedResidual)
        } else rawError

        val denominator = 0.98 + (x * x * priorP)
        if (denominator <= 1e-12) return state
        val gain = (priorP * x) / denominator

        var updatedTheta = priorTheta + (gain * errorForTheta)
        var updatedP = (1.0 / 0.98) * (1.0 - gain * x) * priorP

        if (updatedTheta <= 0.0) updatedTheta = priorTheta
        if (updatedP < 1e-6) updatedP = floor

        val newNeff = (0.98 * priorNeff) + 1.0
        val postGainFactor = max(0.0, 1.0 - gain * x)
        val newRss = (0.98 * priorRss) + (postGainFactor * errorForRss * errorForRss)

        return RlsState(theta = updatedTheta, covariance = updatedP, sampleCount = state.sampleCount + 1, residualSumOfSquares = newRss, effectiveSampleSize = newNeff)
    }

    private fun fitEstimator(type: EstimatorType, obs: List<Pair<Double, Double>>, targetPrior: Double): Double {
        return when (type) {
            EstimatorType.RLS_LAMBDA_098_PRIOR -> {
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.98, initialThetaPrior = targetPrior)
                engine.processSequence(obs).last().theta
            }
            EstimatorType.CUMULATIVE -> {
                val sumD = obs.sumOf { it.first }
                val sumF = obs.sumOf { it.second }
                if (sumF > 0.0) sumD / sumF else 0.0
            }
            EstimatorType.RLS_LAMBDA_095 -> {
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.95)
                engine.processSequence(obs).last().theta
            }
            EstimatorType.RLS_LAMBDA_099 -> {
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.99)
                engine.processSequence(obs).last().theta
            }
            EstimatorType.RLS_LAMBDA_0995 -> {
                val engine = RecursiveLeastSquaresEngine(forgettingFactor = 0.995)
                engine.processSequence(obs).last().theta
            }
            EstimatorType.ROBUST_CUMULATIVE -> {
                // Trimmed Cumulative Ratio (drop max/min 10% outliers if N >= 5)
                if (obs.size >= 5) {
                    val ratios = obs.map { it.first / it.second }.sorted()
                    val trimCount = (ratios.size * 0.1).toInt()
                    val trimmedRatios = ratios.subList(trimCount, ratios.size - trimCount)
                    trimmedRatios.average()
                } else {
                    val sumD = obs.sumOf { it.first }
                    val sumF = obs.sumOf { it.second }
                    if (sumF > 0.0) sumD / sumF else 0.0
                }
            }
        }
    }

    private fun processSequenceWithExplicitPrior(
        observations: List<Pair<Double, Double>>,
        pTheta: Double,
        pCov: Double
    ): List<RlsState> {
        val engine = RecursiveLeastSquaresEngine(
            forgettingFactor = 0.98,
            initialThetaPrior = pTheta,
            initialCovariancePrior = pCov
        )
        return engine.processSequence(observations)
    }

    private fun computeRobustMoe95(state: RlsState, observations: List<Pair<Double, Double>>): Double {
        if (state.sampleCount < 2 || state.effectiveSampleSize <= 1.0) return 0.0
        val theta = state.theta
        val N = observations.size

        // Huber-White / Sandwich heteroskedasticity-robust variance scalar
        var numSum = 0.0
        var denSum = 0.0

        for ((dist, fuel) in observations) {
            val pred = theta * fuel
            val resid = dist - pred
            numSum += (fuel * resid).pow(2)
            denSum += fuel * fuel
        }

        val robustVarTheta = if (denSum > 1e-12) numSum / (denSum * denSum) else state.covariance
        val robustSe = sqrt(max(0.0, robustVarTheta))

        val dof = max(1, (state.effectiveSampleSize - 1.0).toInt())
        val tCrit = RlsState.getTCriticalValue95(dof)
        return tCrit * robustSe
    }

    private fun processAdaptiveForgetting(obs: List<Pair<Double, Double>>): List<RlsState> {
        val states = mutableListOf<RlsState>()
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
                continue
            }

            val pred = theta * x
            val err = y - pred

            // Variable Forgetting Factor (Fortescue VFF)
            val varEst = if (neff > 1.0) rss / (neff - 1.0) else 100.0
            val normalizedErrSq = (err * err) / max(1.0, varEst)
            val lambdaK = min(0.995, max(0.90, 1.0 - (0.05 * normalizedErrSq)))

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

        return states
    }
}
