package com.johnpaulpatigas.motofuel.data.repository

import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import kotlinx.coroutines.flow.Flow

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * Repository interface for managing refueling logs and running estimation.
 */
interface RefuelingRepository {
    fun getDerivedRecords(): Flow<List<RefuelingRecord>>
    fun getEstimationResult(
        tankCapacityLiters: Double = 0.0,
        targetKmLiters: Double = 0.0,
        currencySymbol: String = "₱"
    ): Flow<EstimationResult>

    suspend fun validateAndAdd(
        odometerKm: Double,
        litersAdded: Double,
        pricePerLiter: Double? = null
    ): ValidationResult

    suspend fun validateAndUpdate(
        id: Long,
        odometerKm: Double,
        litersAdded: Double,
        pricePerLiter: Double? = null
    ): ValidationResult
    suspend fun deleteRecord(id: Long)
    suspend fun deleteAllRecords()
    suspend fun getRecordById(id: Long): RefuelingRecord?

    suspend fun exportDataJson(): String
    suspend fun importDataJson(jsonStr: String): ValidationResult
    suspend fun exportDataCsv(): String
    suspend fun importDataCsv(csvStr: String): ValidationResult
}
