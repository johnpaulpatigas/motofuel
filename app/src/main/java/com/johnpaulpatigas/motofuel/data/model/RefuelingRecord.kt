package com.johnpaulpatigas.motofuel.data.model

/**
 * Domain representation of a refueling log entry, enriched with calculated interval metrics.
 */
data class RefuelingRecord(
    val id: Long = 0,
    val odometerKm: Double,
    val litersAdded: Double,
    val pricePerLiter: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val intervalKm: Double? = null,
    val intervalKmLiters: Double? = null,
    val cumulativeKmLiters: Double? = null,
    val isBaseline: Boolean = false
) {
    val totalCost: Double?
        get() = pricePerLiter?.let { price -> price * litersAdded }
}
