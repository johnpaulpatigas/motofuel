package com.johnpaulpatigas.motofuel.data.local

/**
 * Utility class for exporting and importing MotoFuel refueling records to/from JSON strings.
 * Built with pure Kotlin string formatting and parsing for 100% offline portability and JVM test compatibility.
 */
object DataExporter {

    fun exportToJson(entities: List<RefuelingEntity>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        for (i in entities.indices) {
            val entity = entities[i]
            sb.append("  {\n")
            sb.append("    \"id\": ${entity.id},\n")
            sb.append("    \"odometerKm\": ${entity.odometerKm},\n")
            sb.append("    \"litersAdded\": ${entity.litersAdded},\n")
            if (entity.pricePerLiter != null) {
                sb.append("    \"pricePerLiter\": ${entity.pricePerLiter},\n")
            }
            sb.append("    \"timestamp\": ${entity.timestamp}\n")
            sb.append("  }")
            if (i < entities.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun importFromJson(jsonString: String): List<RefuelingEntity> {
        val list = mutableListOf<RefuelingEntity>()
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return emptyList()

        val objectRegex = Regex("""\{[^}]*\}""")
        val matches = objectRegex.findAll(trimmed)

        for (match in matches) {
            val block = match.value
            val odoMatch = Regex(""""odometerKm"\s*:\s*([\d.]+)""").find(block)
            val litersMatch = Regex(""""litersAdded"\s*:\s*([\d.]+)""").find(block)
            val priceMatch = Regex(""""pricePerLiter"\s*:\s*([\d.]+)""").find(block)
            val timestampMatch = Regex(""""timestamp"\s*:\s*(\d+)""").find(block)

            val odo = odoMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val liters = litersMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val price = priceMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val timestamp = timestampMatch?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis()

            if (odo != null && liters != null && odo > 0.0 && liters > 0.0) {
                list.add(
                    RefuelingEntity(
                        odometerKm = odo,
                        litersAdded = liters,
                        pricePerLiter = price,
                        timestamp = timestamp
                    )
                )
            }
        }
        return list.sortedWith(
            compareBy<RefuelingEntity> { it.timestamp }.thenBy { it.odometerKm }
        )
    }
}
