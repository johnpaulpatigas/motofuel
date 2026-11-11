package com.johnpaulpatigas.motofuel

import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepositoryImpl
import com.johnpaulpatigas.motofuel.data.repository.ValidationResult
import com.johnpaulpatigas.motofuel.engine.EfficiencyChangeType
import com.johnpaulpatigas.motofuel.engine.EstimationEngine
import com.johnpaulpatigas.motofuel.repository.FakeRefuelingDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration Test Suite for MotoFuel repository and estimation flows:
 * 1. Initial refueling records
 * 2. Normal partial refueling
 * 3. Efficiency deterioration (CUSUM drop signal)
 * 4. Efficiency improvement (CUSUM jump signal)
 * 5. Incorrect odometer entry
 * 6. Incorrect fuel entry
 * 7. Historical record correction
 * 8. Historical record deletion
 * 9. Backup and restore (JSON/CSV serialization and re-evaluation)
 * 10. Primary cumulative estimate integrity
 */
class RefuelingIntegrationTest {

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
    fun `1 - Initial refueling records establish baseline without false estimates`() = runBlocking {
        // 0 records
        val initialEstimation = repository.getEstimationResult().first()
        assertEquals(0, initialEstimation.recordCount)
        assertEquals(0, initialEstimation.intervalCount)
        assertNull(initialEstimation.cumulativeKmLiters)

        // 1 record (Baseline fill)
        val add1 = repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        assertTrue(add1 is ValidationResult.Success)

        val records1 = repository.getDerivedRecords().first()
        assertEquals(1, records1.size)
        assertTrue(records1[0].isBaseline)
        assertNull(records1[0].intervalKm)

        val est1 = repository.getEstimationResult().first()
        assertEquals(1, est1.recordCount)
        assertEquals(0, est1.intervalCount)
        assertEquals(0.0, est1.totalDistanceKm, 1e-4)
        assertNull(est1.cumulativeKmLiters)

        // 2 records (First interval)
        val add2 = repository.validateAndAdd(odometerKm = 1250.0, litersAdded = 8.0)
        assertTrue(add2 is ValidationResult.Success)

        val records2 = repository.getDerivedRecords().first()
        assertEquals(2, records2.size)
        assertEquals(250.0, records2[1].intervalKm!!, 1e-4)
        assertEquals(31.25, records2[1].intervalKmLiters!!, 1e-4)

        val est2 = repository.getEstimationResult().first()
        assertEquals(1, est2.intervalCount)
        assertEquals(250.0, est2.totalDistanceKm, 1e-4)
        assertEquals(8.0, est2.totalFuelConsumedLiters, 1e-4)
        assertEquals(31.25, est2.cumulativeKmLiters!!, 1e-4)
    }

    @Test
    fun `2 - Normal partial refueling excludes baseline fuel and sums accurately`() = runBlocking {
        // Baseline: 10,000 km, 10 L
        repository.validateAndAdd(odometerKm = 10000.0, litersAdded = 10.0, pricePerLiter = 60.0)
        // Fill 1: +150 km, 5 L
        repository.validateAndAdd(odometerKm = 10150.0, litersAdded = 5.0, pricePerLiter = 60.0)
        // Fill 2: +150 km, 4 L
        repository.validateAndAdd(odometerKm = 10300.0, litersAdded = 4.0, pricePerLiter = 60.0)
        // Fill 3: +200 km, 6 L
        repository.validateAndAdd(odometerKm = 10500.0, litersAdded = 6.0, pricePerLiter = 60.0)

        val est = repository.getEstimationResult().first()

        assertEquals(4, est.recordCount)
        assertEquals(3, est.intervalCount)
        assertEquals(500.0, est.totalDistanceKm, 1e-4)
        assertEquals(15.0, est.totalFuelConsumedLiters, 1e-4) // 5 + 4 + 6
        assertEquals(25.0, est.totalFuelAddedLiters, 1e-4)   // 10 baseline + 15 consumed

        // Cumulative km/L = 500 / 15 = 33.333
        assertEquals(33.333, est.cumulativeKmLiters!!, 0.01)
    }

    @Test
    fun `3 - Efficiency deterioration triggers CUSUM drop signal`() = runBlocking {
        // Establish baseline and normal refuels
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        for (i in 1..10) {
            repository.validateAndAdd(odometerKm = 1000.0 + i * 175.0, litersAdded = 5.0) // ~35 km/L
        }

        // Introduce substantial efficiency deterioration (e.g., 18 km/L)
        var lastOdo = 1000.0 + 10 * 175.0
        repeat(15) {
            lastOdo += 90.0
            repository.validateAndAdd(odometerKm = lastOdo, litersAdded = 5.0)
        }

        val est = repository.getEstimationResult().first()
        assertEquals(EfficiencyChangeType.EFFICIENCY_DROP, est.efficiencyChangeSignal)
    }

    @Test
    fun `4 - Efficiency improvement triggers CUSUM jump signal`() = runBlocking {
        // Establish baseline and lower initial fuel economy (~25 km/L)
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        for (i in 1..10) {
            repository.validateAndAdd(odometerKm = 1000.0 + i * 125.0, litersAdded = 5.0)
        }

        // Introduce sudden efficiency improvement (~45 km/L)
        var lastOdo = 1000.0 + 10 * 125.0
        repeat(15) {
            lastOdo += 225.0
            repository.validateAndAdd(odometerKm = lastOdo, litersAdded = 5.0)
        }

        val est = repository.getEstimationResult().first()
        assertEquals(EfficiencyChangeType.EFFICIENCY_IMPROVEMENT, est.efficiencyChangeSignal)
    }

    @Test
    fun `5 - One incorrect odometer entry is rejected by validation`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        repository.validateAndAdd(odometerKm = 1200.0, litersAdded = 5.0)

        // Lower odometer reading
        val result = repository.validateAndAdd(odometerKm = 1150.0, litersAdded = 5.0)
        assertTrue(result is ValidationResult.Error)
        val err = result as ValidationResult.Error
        assertTrue(err.message.contains("cannot be less than"))
    }

    @Test
    fun `6 - One incorrect fuel entry is rejected by validation`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)

        val zeroFuel = repository.validateAndAdd(odometerKm = 1200.0, litersAdded = 0.0)
        assertTrue(zeroFuel is ValidationResult.Error)

        val negFuel = repository.validateAndAdd(odometerKm = 1200.0, litersAdded = -5.0)
        assertTrue(negFuel is ValidationResult.Error)
    }

    @Test
    fun `7 - Historical record correction triggers full chronological re-evaluation`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        repository.validateAndAdd(odometerKm = 1200.0, litersAdded = 5.0) // Record 2: +200 km, 5 L
        repository.validateAndAdd(odometerKm = 1400.0, litersAdded = 5.0) // Record 3: +200 km, 5 L

        val records = repository.getDerivedRecords().first()
        val record2 = records[1]

        // Update record 2 to correct odometer from 1200 to 1250 km
        val updateRes = repository.validateAndUpdate(
            id = record2.id,
            odometerKm = 1250.0,
            litersAdded = 5.0,
            pricePerLiter = null
        )
        assertTrue(updateRes is ValidationResult.Success)

        val updatedRecords = repository.getDerivedRecords().first()
        assertEquals(250.0, updatedRecords[1].intervalKm!!, 1e-4) // 1250 - 1000
        assertEquals(150.0, updatedRecords[2].intervalKm!!, 1e-4) // 1400 - 1250

        val updatedEst = repository.getEstimationResult().first()
        assertEquals(400.0, updatedEst.totalDistanceKm, 1e-4)
        assertEquals(10.0, updatedEst.totalFuelConsumedLiters, 1e-4)
        assertEquals(40.0, updatedEst.cumulativeKmLiters!!, 1e-4)
    }

    @Test
    fun `8 - Historical record deletion re-derives adjacent intervals chronologically`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0) // Rec 1
        repository.validateAndAdd(odometerKm = 1200.0, litersAdded = 5.0)  // Rec 2
        repository.validateAndAdd(odometerKm = 1500.0, litersAdded = 10.0) // Rec 3

        val records = repository.getDerivedRecords().first()
        val rec2Id = records[1].id

        repository.deleteRecord(rec2Id)

        val recordsAfter = repository.getDerivedRecords().first()
        assertEquals(2, recordsAfter.size)
        assertTrue(recordsAfter[0].isBaseline)
        assertEquals(500.0, recordsAfter[1].intervalKm!!, 1e-4) // 1500 - 1000

        val estAfter = repository.getEstimationResult().first()
        assertEquals(1, estAfter.intervalCount)
        assertEquals(500.0, estAfter.totalDistanceKm, 1e-4)
        assertEquals(10.0, estAfter.totalFuelConsumedLiters, 1e-4)
        assertEquals(50.0, estAfter.cumulativeKmLiters!!, 1e-4)
    }

    @Test
    fun `9 - Backup and restore preserves mathematical integrity across serialization`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0, pricePerLiter = 60.0)
        repository.validateAndAdd(odometerKm = 1300.0, litersAdded = 8.0, pricePerLiter = 62.0)
        repository.validateAndAdd(odometerKm = 1650.0, litersAdded = 10.0, pricePerLiter = 61.0)

        val origEst = repository.getEstimationResult(tankCapacityLiters = 10.0).first()
        val jsonStr = repository.exportDataJson()

        // Wipe data
        repository.deleteAllRecords()
        val emptyEst = repository.getEstimationResult().first()
        assertEquals(0, emptyEst.recordCount)

        // Restore data
        val importRes = repository.importDataJson(jsonStr)
        assertTrue(importRes is ValidationResult.Success)

        val restoredEst = repository.getEstimationResult(tankCapacityLiters = 10.0).first()
        assertEquals(origEst.recordCount, restoredEst.recordCount)
        assertEquals(origEst.totalDistanceKm, restoredEst.totalDistanceKm, 1e-4)
        assertEquals(origEst.totalFuelConsumedLiters, restoredEst.totalFuelConsumedLiters, 1e-4)
        assertEquals(origEst.cumulativeKmLiters!!, restoredEst.cumulativeKmLiters!!, 1e-4)
        assertEquals(origEst.estimatedRangeKm!!, restoredEst.estimatedRangeKm!!, 1e-4)
    }

    @Test
    fun `10 - Primary cumulative estimate is never overwritten by RLS or rolling window`() = runBlocking {
        repository.validateAndAdd(odometerKm = 5000.0, litersAdded = 10.0)
        repository.validateAndAdd(odometerKm = 5150.0, litersAdded = 4.0) // 37.5 km/L
        repository.validateAndAdd(odometerKm = 5300.0, litersAdded = 6.0) // 25.0 km/L
        repository.validateAndAdd(odometerKm = 5500.0, litersAdded = 5.0) // 40.0 km/L

        val est = repository.getEstimationResult(tankCapacityLiters = 8.0).first()

        assertEquals(500.0, est.totalDistanceKm, 1e-4)
        assertEquals(15.0, est.totalFuelConsumedLiters, 1e-4)

        val expectedCumulative = 500.0 / 15.0 // 33.3333

        // Verify primary cumulative estimate strictly equals Total Distance / Total Consumed
        assertEquals(expectedCumulative, est.cumulativeKmLiters!!, 1e-4)

        // Verify full tank range uses cumulative estimate
        assertEquals(expectedCumulative * 8.0, est.estimatedRangeKm!!, 1e-4)

        // Verify RLS predicted km/L and rolling window km/L are separate distinct values
        assertNotNull(est.predictedKmLiters)
        assertNotNull(est.rollingWindowKmLiters)

        // Confirm cumulative km/L is distinct from RLS predicted km/L under noisy intervals
        assertTrue(est.cumulativeKmLiters != est.predictedKmLiters)
    }
}
