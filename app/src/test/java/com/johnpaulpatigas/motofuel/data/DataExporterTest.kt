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
    fun `exportToCsv and importFromCsv roundtrip preserves entity values`() {
        val original = listOf(
            RefuelingEntity(id = 1, odometerKm = 5000.0, litersAdded = 12.0, timestamp = 1500000L),
            RefuelingEntity(id = 2, odometerKm = 5300.0, litersAdded = 9.0, timestamp = 2500000L)
        )

        val csv = DataExporter.exportToCsv(original)
        assertTrue(csv.contains("5000.0"))
        assertTrue(csv.contains("12.0"))

        val imported = DataExporter.importFromCsv(csv)
        assertEquals(2, imported.size)
        assertEquals(5000.0, imported[0].odometerKm, 0.001)
        assertEquals(12.0, imported[0].litersAdded, 0.001)
        assertEquals(5300.0, imported[1].odometerKm, 0.001)
        assertEquals(9.0, imported[1].litersAdded, 0.001)
    }
}
