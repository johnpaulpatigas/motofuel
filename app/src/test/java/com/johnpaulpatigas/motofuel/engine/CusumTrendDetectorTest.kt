package com.johnpaulpatigas.motofuel.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CusumTrendDetectorTest {

    @Test
    fun `test CUSUM initial state and stationary sequence`() {
        val detector = CusumTrendDetector(kappa = 0.75, thresholdH = 5.0)

        // Innovations near zero
        val innovations = listOf(
            Pair(0.1, 1.0),
            Pair(-0.2, 1.0),
            Pair(0.05, 1.0)
        )

        val states = detector.processSequence(innovations)
        assertEquals(3, states.size)
        assertEquals(EfficiencyChangeType.NONE, states.last().signal)
        assertEquals(0.0, states.last().gPlus, 1e-4)
        assertEquals(0.0, states.last().gMinus, 1e-4)
    }

    @Test
    fun `test CUSUM efficiency drop signal`() {
        val detector = CusumTrendDetector(kappa = 0.5, thresholdH = 3.0)

        // Persistent negative innovations (distance deficit = efficiency drop)
        val negativeInnovations = List(5) { Pair(-2.5, 1.0) } // z = -2.5, -z - 0.5 = +2.0 per step

        val states = detector.processSequence(negativeInnovations)
        // Step 1: gPlus = max(0, 0 + 2.5 - 0.5) = 2.0
        // Step 2: gPlus = max(0, 2.0 + 2.5 - 0.5) = 4.0 >= h=3.0 -> EFFICIENCY_DROP
        assertEquals(EfficiencyChangeType.EFFICIENCY_DROP, states.last().signal)
    }

    @Test
    fun `test CUSUM single outlier decay without false alarm`() {
        val detector = CusumTrendDetector(kappa = 0.75, thresholdH = 5.0)

        // Single large negative residual followed by compensating positive residual
        val sequence = listOf(
            Pair(-3.5, 1.0), // z = -3.5, gPlus = max(0, 3.5 - 0.75) = 2.75 < 5.0
            Pair(3.5, 1.0)   // z = +3.5, gPlus = max(0, 2.75 - 3.5 - 0.75) = 0.0
        )

        val states = detector.processSequence(sequence)
        assertEquals(2.75, states[0].gPlus, 1e-4)
        assertEquals(EfficiencyChangeType.NONE, states[0].signal)

        assertEquals(0.0, states[1].gPlus, 1e-4)
        assertEquals(EfficiencyChangeType.NONE, states[1].signal)
    }
}
