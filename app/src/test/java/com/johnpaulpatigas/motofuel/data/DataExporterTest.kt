package com.johnpaulpatigas.motofuel.data

import com.johnpaulpatigas.motofuel.data.local.DataExporter
import com.johnpaulpatigas.motofuel.data.local.RefuelingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExporterTest {

    @Test
    fun `exportToJson and importFromJson roundtrip preserves entity values`() {
        val original = listOf(
            RefuelingEntity(id = 1, odometerKm = 1000.0, litersAdded = 10.0, timestamp = 1000000L),
            RefuelingEntity(id = 2, odometerKm = 1250.0, litersAdded = 8.5, timestamp = 2000000L)
        )

        val json = DataExporter.exportToJson(original)
        assertTrue(json.contains("1000.0"))
        assertTrue(json.contains("8.5"))

        val imported = DataExporter.importFromJson(json)
        assertEquals(2, imported.size)
        assertEquals(1000.0, imported[0].odometerKm, 0.001)
        assertEquals(10.0, imported[0].litersAdded, 0.001)
        assertEquals(1250.0, imported[1].odometerKm, 0.001)
        assertEquals(8.5, imported[1].litersAdded, 0.001)
    }

    @Test
    fun `importFromJson resolves timestamp ties using odometer ascending`() {
        val sameTimestamp = 1700000000000L
        val original = listOf(
            RefuelingEntity(id = 2, odometerKm = 1500.0, litersAdded = 5.0, timestamp = sameTimestamp),
            RefuelingEntity(id = 1, odometerKm = 1200.0, litersAdded = 8.0, timestamp = sameTimestamp)
        )

        val json = DataExporter.exportToJson(original)
        val imported = DataExporter.importFromJson(json)

        assertEquals(2, imported.size)
        assertEquals(1200.0, imported[0].odometerKm, 0.001)
        assertEquals(1500.0, imported[1].odometerKm, 0.001)
    }
}
