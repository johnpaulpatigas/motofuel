package com.johnpaulpatigas.motofuel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecursiveLeastSquaresEngineTest {

    private lateinit var rlsEngine: RecursiveLeastSquaresEngine

    @Before
    fun setUp() {
        rlsEngine = RecursiveLeastSquaresEngine(forgettingFactor = 0.95)
    }

    @Test
    fun `RlsState initial state has zero error and count`() {
        val state = RlsState()
        assertEquals(0.0, state.theta, 0.0001)
        assertEquals(100.0, state.covariance, 0.0001)
        assertEquals(0, state.sampleCount)
        assertEquals(0.0, state.standardError, 0.0001)
        assertEquals(0.0, state.marginOfError95, 0.0001)
    }

    @Test
    fun `update - single observation updates parameter toward empirical ratio`() {
        val state0 = RlsState()
        // Observation: 300 km on 10.0 liters -> ratio = 30.0 km/L
        val state1 = rlsEngine.update(state0, distanceKm = 300.0, litersAdded = 10.0)

        assertEquals(1, state1.sampleCount)
        assertTrue(state1.theta > 0.0)
        assertTrue(state1.covariance < state0.covariance) // Covariance decreases
    }

    @Test
    fun `update - ignores invalid or zero inputs`() {
        val state0 = RlsState()
        val stateInvalidFuel = rlsEngine.update(state0, distanceKm = 200.0, litersAdded = 0.0)
        assertEquals(0, stateInvalidFuel.sampleCount)

        val stateInvalidDist = rlsEngine.update(state0, distanceKm = -50.0, litersAdded = 5.0)
        assertEquals(0, stateInvalidDist.sampleCount)
    }

    @Test
    fun `processSequence - converges toward true efficiency across observations`() {
        val trueKmLiters = 35.0
        // Generate observations around 35 km/L with slight pump noise
        val observations = listOf(
            Pair(350.0, 10.0), // 35.0 km/L
            Pair(360.0, 10.0), // 36.0 km/L
            Pair(340.0, 10.0), // 34.0 km/L
            Pair(350.0, 10.0), // 35.0 km/L
            Pair(355.0, 10.0)  // 35.5 km/L
        )

        val states = rlsEngine.processSequence(observations)
        assertEquals(5, states.size)

        val finalState = states.last()
        assertEquals(5, finalState.sampleCount)
        // Check convergence close to true efficiency
        assertEquals(trueKmLiters, finalState.theta, 1.5)
        assertTrue(finalState.standardError >= 0.0)
        assertFalse(finalState.standardError.isNaN())
    }

    @Test
    fun `deterministic recalculation - processing sequence produces identical state`() {
        val observations = listOf(
            Pair(200.0, 5.0),
            Pair(300.0, 8.0),
            Pair(250.0, 6.5)
        )

        val run1 = rlsEngine.processSequence(observations).last()
        val run2 = rlsEngine.processSequence(observations).last()

        assertEquals(run1.theta, run2.theta, 1e-9)
        assertEquals(run1.covariance, run2.covariance, 1e-9)
        assertEquals(run1.residualSumOfSquares, run2.residualSumOfSquares, 1e-9)
    }

    @Test
    fun `processSequence - recurring 1L partial fills with fluctuation converge toward underlying mean`() {
        val trueEconomy = 40.0 // True bike efficiency: 40 km/L
        // Rider refuels 1.0L every other day, with distances fluctuating (e.g. 25km, 55km, 30km, 50km)
        // because fuel was not completely consumed
        val partialFillObservations = listOf(
            Pair(25.0, 1.0),
            Pair(55.0, 1.0),
            Pair(30.0, 1.0),
            Pair(50.0, 1.0),
            Pair(28.0, 1.0),
            Pair(52.0, 1.0),
            Pair(38.0, 1.0),
            Pair(42.0, 1.0),
            Pair(35.0, 1.0),
            Pair(45.0, 1.0)
        )

        val states = rlsEngine.processSequence(partialFillObservations)
        val finalState = states.last()

        assertEquals(10, finalState.sampleCount)
        // Verify RLS smooths out the fluctuations and converges close to 40 km/L
        assertEquals(trueEconomy, finalState.theta, 3.5)
        // High variation in individual intervals produces measurable non-zero uncertainty
        assertTrue(finalState.standardError > 0.0)
        assertTrue(finalState.marginOfError95 > 0.0)
    }

    @Test
    fun `RlsState - Student t critical values match degrees of freedom`() {
        assertEquals(12.706, RlsState.getTCriticalValue95(1), 0.001)
        assertEquals(4.303, RlsState.getTCriticalValue95(2), 0.001)
        assertEquals(3.182, RlsState.getTCriticalValue95(3), 0.001)
        assertEquals(2.228, RlsState.getTCriticalValue95(10), 0.001)
        assertEquals(1.96, RlsState.getTCriticalValue95(30), 0.001)
    }

    @Test
    fun `update - Huber robust loss suppresses extreme outlier distortion`() {
        // Process a few baseline observations around 30 km/L
        var state = RlsState()
        state = rlsEngine.update(state, 300.0, 10.0) // 30 km/L
        state = rlsEngine.update(state, 300.0, 10.0) // 30 km/L
        state = rlsEngine.update(state, 300.0, 10.0) // 30 km/L
        val thetaBeforeOutlier = state.theta

        // Introduce a massive outlier (e.g., 3000 km interval on 10L = 300 km/L typo)
        val stateWithOutlier = rlsEngine.update(state, 3000.0, 10.0)

        // Verify that Huber attenuation prevents theta from jumping wildly toward 300 km/L
        assertTrue(stateWithOutlier.theta < thetaBeforeOutlier + 10.0)
    }

    @Test
    fun `initialization - prior based engine smoothly blends prior with initial observation`() {
        val priorEngine = RecursiveLeastSquaresEngine(initialThetaPrior = 35.0, initialCovariancePrior = 25.0)
        val state0 = RlsState()
        // Sample 1: Small 1.0L partial fill with 50 km (50 km/L empirical ratio)
        val state1 = priorEngine.update(state0, distanceKm = 50.0, litersAdded = 1.0)

        // Instead of assigning 50.0 directly, theta smoothly blends between prior 35.0 and 50.0
        assertTrue("Theta (${state1.theta}) should be between prior 35.0 and empirical 50.0", state1.theta > 35.0 && state1.theta < 50.0)
        assertEquals(1, state1.sampleCount)
    }

    @Test
    fun `initialization - covariance floor prevents premature covariance collapse on sample 1`() {
        val defaultEngine = RecursiveLeastSquaresEngine()
        val state0 = RlsState()
        // Sample 1 with 10L refuel: 10^2 * 100 = 10000 in denominator
        val state1 = defaultEngine.update(state0, distanceKm = 350.0, litersAdded = 10.0)

        // Covariance floor ensures P1 is at least 2.5 rather than collapsing to ~0.01
        assertTrue("Covariance (${state1.covariance}) should be floor bounded at >= 2.5", state1.covariance >= 2.5)
    }

    @Test
    fun `decoupled robust residual variance - captures partial fill scatter accurately`() {
        val engine = RecursiveLeastSquaresEngine()
        val obs = listOf(
            Pair(200.0, 5.0), // 40 km/L
            Pair(150.0, 5.0), // 30 km/L
            Pair(250.0, 5.0), // 50 km/L
            Pair(180.0, 5.0)  // 36 km/L
        )

        val states = engine.processSequence(obs)
        val finalState = states.last()

        // Verify parameter theta is updated smoothly
        assertEquals(34.0, finalState.theta, 3.0)
        // Decoupled RSS captures non-zero residual variance producing valid positive standard error
        assertTrue("Standard error (${finalState.standardError}) must be > 0.0", finalState.standardError > 0.5)
        assertTrue("Margin of error (${finalState.marginOfError95}) must be > 0.0", finalState.marginOfError95 > 1.0)
    }
}
