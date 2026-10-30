package com.johnpaulpatigas.motofuel.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single refueling log persisted on device.
 *
 * Each record stores the user-entered odometer reading (km) and fuel added (L),
 * alongside the creation timestamp and calculated interval distance.
 */
@Entity(
    tableName = "refueling_records",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["odometerKm"])
    ]
)
data class RefuelingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val odometerKm: Double,
    val litersAdded: Double,
    val pricePerLiter: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
