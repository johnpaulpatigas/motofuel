package com.johnpaulpatigas.motofuel.engine

import com.johnpaulpatigas.motofuel.data.local.RefuelingEntity
import com.johnpaulpatigas.motofuel.data.model.ReliabilityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EstimationEngineTest {

    private lateinit var engine: EstimationEngine

    @Before
    fun setUp() {
        engine = EstimationEngine()
    }

    @Test
    fun `computeDerivedRecords - empty list returns empty list`() {
        val result = engine.computeDerivedRecords(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeDerivedRecords - single record is identified as baseline`() {
        val entity = RefuelingEntity(id = 1, odometerKm = 1000.0, litersAdded = 10.0, timestamp = 1000L)
        val derived = engine.computeDerivedRecords(listOf(entity))

        assertEquals(1, derived.size)
        val record = derived.first()
        assertTrue(record.isBaseline)
        assertNull(record.intervalKm)
        assertNull(record.intervalKmLiters)
        assertEquals(1000.0, record.odometerKm, 0.001)
        assertEquals(10.0, record.litersAdded, 0.001)
    }

    @Test
    fun `computeDerivedRecords - multiple valid records calculate interval distance correctly`() {
        val entities = listOf(
            RefuelingEntity(id = 1, odometerKm = 1000.0, litersAdded = 10.0, timestamp = 1000L),
            RefuelingEntity(id = 2, odometerKm = 1250.0, litersAdded = 8.0, timestamp = 2000L),
            RefuelingEntity(id = 3, odometerKm = 1550.0, litersAdded = 10.0, timestamp = 3000L)
        )

        val derived = engine.computeDerivedRecords(entities)

        assertEquals(3, derived.size)

        // Record 1 (Baseline)
        assertTrue(derived[0].isBaseline)
        assertNull(derived[0].intervalKm)
        assertNull(derived[0].intervalKmLiters)

        // Record 2: Interval = 1250 - 1000 = 250 km. km/L = 250 / 8 = 31.25
        assertFalse(derived[1].isBaseline)
        assertEquals(250.0, derived[1].intervalKm!!, 0.001)
        assertEquals(31.25, derived[1].intervalKmLiters!!, 0.001)

        // Record 3: Interval = 1550 - 1250 = 300 km. km/L = 300 / 10 = 30.0
        assertFalse(derived[2].isBaseline)
        assertEquals(300.0, derived[2].intervalKm!!, 0.001)
        assertEquals(30.0, derived[2].intervalKmLiters!!, 0.001)
    }

    @Test
    fun `computeDerivedRecords - non-increasing or lower odometer yields zero interval and null economy`() {
        val entities = listOf(
            RefuelingEntity(id = 1, odometerKm = 1000.0, litersAdded = 10.0, timestamp = 1000L),
            RefuelingEntity(id = 2, odometerKm = 900.0, litersAdded = 5.0, timestamp = 2000L) // Invalid decrease
        )

        val derived = engine.computeDerivedRecords(entities)
        assertEquals(0.0, derived[1].intervalKm!!, 0.001)
        assertNull(derived[1].intervalKmLiters)
    }

    @Test
    fun `computeDerivedRecords - zero or negative fuel yields null interval fuel economy`() {
        val entities = listOf(
            RefuelingEntity(id = 1, odometerKm = 1000.0, litersAdded = 10.0, timestamp = 1000L),
            RefuelingEntity(id = 2, odometerKm = 1200.0, litersAdded = 0.0, timestamp = 2000L) // Invalid zero fuel
        )

        val derived = engine.computeDerivedRecords(entities)
        assertEquals(200.0, derived[1].intervalKm!!, 0.001)
        assertNull(derived[1].intervalKmLiters)
    }

    @Test
    fun `evaluate - insufficient data when no records or 1 baseline record`() {
        val result0 = engine.evaluate(emptyList())
        assertEquals(ReliabilityLevel.INSUFFICIENT_DATA, result0.reliability)

        val baselineDerived = engine.computeDerivedRecords(
            listOf(RefuelingEntity(id = 1, odometerKm = 5000.0, litersAdded = 12.0))
        )
        val result1 = engine.evaluate(baselineDerived)

        assertEquals(ReliabilityLevel.INSUFFICIENT_DATA, result1.reliability)
        assertEquals(1, result1.recordCount)
        assertEquals(0, result1.intervalCount)
        assertEquals(0.0, result1.totalDistanceKm, 0.001)
        assertEquals(0.0, result1.totalFuelConsumedLiters, 0.001)
        assertEquals(12.0, result1.totalFuelAddedLiters, 0.001)
        assertNull(result1.cumulativeKmLiters)
    }

    @Test
    fun `evaluate - cumulative and recent calculations exclude baseline fuel from consumed fuel`() {
        // Record 0 (Baseline): Odo 10,000, Fuel 15.0 L
        // Record 1: Odo 10,200 (+200 km), Fuel 5.0 L -> km/L = 40.0
        // Record 2: Odo 10,500 (+300 km), Fuel 10.0 L -> km/L = 30.0
        val entities = listOf(
            RefuelingEntity(id = 1, odometerKm = 10000.0, litersAdded = 15.0, timestamp = 1000L),
            RefuelingEntity(id = 2, odometerKm = 10200.0, litersAdded = 5.0, timestamp = 2000L),
            RefuelingEntity(id = 3, odometerKm = 10500.0, litersAdded = 10.0, timestamp = 3000L)
        )

        val derived = engine.computeDerivedRecords(entities)
        val result = engine.evaluate(derived)

        assertEquals(ReliabilityLevel.MEDIUM, result.reliability)
        assertEquals(3, result.recordCount)
        assertEquals(2, result.intervalCount)

        assertEquals(500.0, result.totalDistanceKm, 0.001)
        assertEquals(15.0, result.totalFuelConsumedLiters, 0.001) // 5.0 + 10.0
        assertEquals(30.0, result.totalFuelAddedLiters, 0.001)   // 15.0 baseline + 15.0 consumed

        // Cumulative km/L = 500 / 15 = 33.333
        assertEquals(33.333, result.cumulativeKmLiters!!, 0.01)
        assertNotNull(result.predictedKmLiters)
        assertNotNull(result.uncertaintyMargin)
    }

    @Test
    fun `evaluate - extreme values and numerical stability`() {
        val entities = listOf(
            RefuelingEntity(id = 1, odometerKm = 999999.0, litersAdded = 100.0, timestamp = 1000L),
            RefuelingEntity(id = 2, odometerKm = 1000499.0, litersAdded = 12.5, timestamp = 2000L)
        )

        val derived = engine.computeDerivedRecords(entities)
        val result = engine.evaluate(derived)

        assertEquals(500.0, result.totalDistanceKm, 0.001)
        assertEquals(12.5, result.totalFuelConsumedLiters, 0.001)
        assertEquals(40.0, result.cumulativeKmLiters!!, 0.001)
        assertFalse(result.predictedKmLiters!!.isNaN())
        assertFalse(result.predictedKmLiters!!.isInfinite())
    }
}
