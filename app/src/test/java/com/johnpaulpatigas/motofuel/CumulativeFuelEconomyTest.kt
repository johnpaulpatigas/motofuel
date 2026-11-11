package com.johnpaulpatigas.motofuel

import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepositoryImpl
import com.johnpaulpatigas.motofuel.engine.EstimationEngine
import com.johnpaulpatigas.motofuel.repository.FakeRefuelingDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying MotoFuel's cumulative fuel economy logic:
 * 1. Primary user-facing fuel economy calculation is strictly cumulativeKmLiters = totalDistanceKm / totalConsumedFuelLiters.
 * 2. Supporting statistics (full-tank range, cost per km, target efficiency delta) derive from the cumulative estimate.
 * 3. Secondary estimators (RLS, rolling window) do not overwrite or replace the primary cumulative value.
 */
class CumulativeFuelEconomyTest {

    private lateinit var dao: FakeRefuelingDao
    private lateinit var repository: RefuelingRepositoryImpl
    private lateinit var engine: EstimationEngine

    @Before
    fun setUp() {
        dao = FakeRefuelingDao()
        engine = EstimationEngine()
        repository = RefuelingRepositoryImpl(dao, engine)
    }

    @Test
    fun `cumulativeKmLiters is strictly calculated as totalDistanceKm divided by totalConsumedFuelLiters`() = runBlocking {
        // Baseline: 1000 km, 10 L
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        // Interval 1: +250 km, 5.0 L
        repository.validateAndAdd(odometerKm = 1250.0, litersAdded = 5.0)
        // Interval 2: +350 km, 10.0 L
        repository.validateAndAdd(odometerKm = 1600.0, litersAdded = 10.0)

        val est = repository.getEstimationResult().first()

        val expectedDistance = 600.0 // 250 + 350
        val expectedConsumedFuel = 15.0 // 5.0 + 10.0
        val expectedCumulative = expectedDistance / expectedConsumedFuel // 40.0 km/L

        assertEquals(expectedDistance, est.totalDistanceKm, 1e-4)
        assertEquals(expectedConsumedFuel, est.totalFuelConsumedLiters, 1e-4)
        assertNotNull(est.cumulativeKmLiters)
        assertEquals(expectedCumulative, est.cumulativeKmLiters!!, 1e-4)
    }

    @Test
    fun `estimatedRangeKm and efficiencyVsTarget derive strictly from cumulative estimate`() = runBlocking {
        repository.validateAndAdd(odometerKm = 5000.0, litersAdded = 12.0)
        repository.validateAndAdd(odometerKm = 5300.0, litersAdded = 10.0) // +300 km, 10 L = 30 km/L
        repository.validateAndAdd(odometerKm = 5700.0, litersAdded = 10.0) // +400 km, 10 L = 40 km/L

        val tankCapacity = 10.0
        val targetKmLiters = 32.0

        val est = repository.getEstimationResult(
            tankCapacityLiters = tankCapacity,
            targetKmLiters = targetKmLiters
        ).first()

        val expectedCumulative = 700.0 / 20.0 // 35.0 km/L
        assertEquals(expectedCumulative, est.cumulativeKmLiters!!, 1e-4)

        // Estimated Range = cumulativeKmLiters * tankCapacity
        val expectedRange = expectedCumulative * tankCapacity
        assertNotNull(est.estimatedRangeKm)
        assertEquals(expectedRange, est.estimatedRangeKm!!, 1e-4)

        // Target Diff = cumulativeKmLiters - targetKmLiters
        val expectedTargetDiff = expectedCumulative - targetKmLiters
        assertNotNull(est.efficiencyVsTarget)
        assertEquals(expectedTargetDiff, est.efficiencyVsTarget!!, 1e-4)
    }

    @Test
    fun `costPerKm derives strictly from totalSpendCost divided by totalDistanceKm`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0, pricePerLiter = 60.0) // Spend 600
        repository.validateAndAdd(odometerKm = 1300.0, litersAdded = 8.0, pricePerLiter = 65.0)  // Spend 520, +300 km
        repository.validateAndAdd(odometerKm = 1700.0, litersAdded = 10.0, pricePerLiter = 62.0) // Spend 620, +400 km

        val est = repository.getEstimationResult().first()

        val expectedTotalSpend = (10.0 * 60.0) + (8.0 * 65.0) + (10.0 * 62.0) // 1740.0
        val expectedTotalDistance = 700.0
        val expectedCostPerKm = expectedTotalSpend / expectedTotalDistance

        assertEquals(expectedTotalSpend, est.totalSpendCost, 1e-4)
        assertEquals(expectedTotalDistance, est.totalDistanceKm, 1e-4)
        assertNotNull(est.costPerKm)
        assertEquals(expectedCostPerKm, est.costPerKm!!, 1e-4)
    }

    @Test
    fun `hidden estimators RLS and rolling window cannot overwrite primary cumulative estimate`() = runBlocking {
        // Create fluctuating interval sequence where RLS and rolling window diverge from cumulative
        repository.validateAndAdd(odometerKm = 10000.0, litersAdded = 10.0)
        repository.validateAndAdd(odometerKm = 10100.0, litersAdded = 5.0) // 20 km/L
        repository.validateAndAdd(odometerKm = 10400.0, litersAdded = 5.0) // 60 km/L
        repository.validateAndAdd(odometerKm = 10500.0, litersAdded = 5.0) // 20 km/L
        repository.validateAndAdd(odometerKm = 10800.0, litersAdded = 5.0) // 60 km/L

        val est = repository.getEstimationResult().first()

        val expectedCumulative = 800.0 / 20.0 // 40.0 km/L
        assertEquals(expectedCumulative, est.cumulativeKmLiters!!, 1e-4)

        // RLS and rolling window calculate internal predictions
        assertNotNull(est.predictedKmLiters)
        assertNotNull(est.rollingWindowKmLiters)

        // Primary cumulative estimate remains total distance / total fuel
        assertEquals(40.0, est.cumulativeKmLiters, 1e-4)
        assertTrue(est.cumulativeKmLiters != est.predictedKmLiters || est.cumulativeKmLiters != est.rollingWindowKmLiters)
    }
}
