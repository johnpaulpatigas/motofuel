package com.johnpaulpatigas.motofuel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RollingWindowEstimatorTest {

    @Test
    fun `test rolling window calculation with sufficient observations`() {
        val estimator = RollingWindowEstimator(windowSize = 3)
        val obs = listOf(
            Pair(100.0, 5.0), // 20 km/L
            Pair(120.0, 4.0), // 30 km/L
            Pair(150.0, 5.0), // 30 km/L
            Pair(200.0, 8.0)  // 25 km/L
        )

        val sequence = estimator.processSequence(obs)
        assertEquals(4, sequence.size)

        // Step 0: 100 / 5 = 20.0
        assertEquals(20.0, sequence[0]!!, 1e-4)
        // Step 1: (100 + 120) / (5 + 4) = 220 / 9 = 24.4444
        assertEquals(24.4444, sequence[1]!!, 1e-4)
        // Step 2: (100 + 120 + 150) / (5 + 4 + 5) = 370 / 14 = 26.4285
        assertEquals(26.4285, sequence[2]!!, 1e-4)
        // Step 3 (Window size 3, last 3): (120 + 150 + 200) / (4 + 5 + 8) = 470 / 17 = 27.6470
        assertEquals(27.6470, sequence[3]!!, 1e-4)

        assertEquals(27.6470, estimator.evaluateLatest(obs)!!, 1e-4)
    }

    @Test
    fun `test rolling window with empty observations`() {
        val estimator = RollingWindowEstimator(windowSize = 5)
        val sequence = estimator.processSequence(emptyList())
        assertEquals(0, sequence.size)
        assertNull(estimator.evaluateLatest(emptyList()))
    }

    @Test
    fun `test rolling window with invalid denominators`() {
        val estimator = RollingWindowEstimator(windowSize = 3)
        val obs = listOf(
            Pair(0.0, 0.0),
            Pair(-10.0, 0.0)
        )
        val sequence = estimator.processSequence(obs)
        assertNull(sequence[0])
        assertNull(sequence[1])
        assertNull(estimator.evaluateLatest(obs))
    }
}
