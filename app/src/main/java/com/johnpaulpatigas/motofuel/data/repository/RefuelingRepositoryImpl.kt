package com.johnpaulpatigas.motofuel.data.repository

import com.johnpaulpatigas.motofuel.data.local.RefuelingDao
import com.johnpaulpatigas.motofuel.data.local.RefuelingEntity
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.engine.EstimationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository implementation connecting Room DAO and EstimationEngine.
 */
class RefuelingRepositoryImpl(
    private val dao: RefuelingDao,
    private val engine: EstimationEngine = EstimationEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RefuelingRepository {

    override fun getDerivedRecords(): Flow<List<RefuelingRecord>> {
        return dao.getAllChronological().map { entities ->
            engine.computeDerivedRecords(entities)
        }.flowOn(dispatcher)
    }

    override fun getEstimationResult(
        tankCapacityLiters: Double,
        targetKmLiters: Double,
        currencySymbol: String
    ): Flow<EstimationResult> {
        return getDerivedRecords().map { records ->
            engine.evaluate(
                records = records,
                tankCapacityLiters = tankCapacityLiters,
                targetKmLiters = targetKmLiters,
                currencySymbol = currencySymbol
            )
        }.flowOn(dispatcher)
    }

    override suspend fun validateAndAdd(
        odometerKm: Double,
        litersAdded: Double,
        pricePerLiter: Double?
    ): ValidationResult = withContext(dispatcher) {
        if (odometerKm <= 0.0) {
            return@withContext ValidationResult.Error("Odometer reading must be greater than 0 km.")
        }
        if (litersAdded <= 0.0) {
            return@withContext ValidationResult.Error("Fuel added must be greater than 0 liters.")
        }
        if (pricePerLiter != null && pricePerLiter < 0.0) {
            return@withContext ValidationResult.Error("Fuel price cannot be negative.")
        }

        val existingEntities = dao.getAllChronologicalList()
        if (existingEntities.isNotEmpty()) {
            val latest = existingEntities.last()
            if (odometerKm < latest.odometerKm) {
                return@withContext ValidationResult.Error("Odometer reading ($odometerKm km) cannot be less than the previous recorded odometer reading (${latest.odometerKm} km).")
            }
        }

        dao.insert(
            RefuelingEntity(
                odometerKm = odometerKm,
                litersAdded = litersAdded,
                pricePerLiter = pricePerLiter
            )
        )
        ValidationResult.Success
    }

    override suspend fun validateAndUpdate(
        id: Long,
        odometerKm: Double,
        litersAdded: Double,
        pricePerLiter: Double?
    ): ValidationResult = withContext(dispatcher) {
        if (odometerKm <= 0.0) {
            return@withContext ValidationResult.Error("Odometer reading must be greater than 0 km.")
        }
        if (litersAdded <= 0.0) {
            return@withContext ValidationResult.Error("Fuel added must be greater than 0 liters.")
        }
        if (pricePerLiter != null && pricePerLiter < 0.0) {
            return@withContext ValidationResult.Error("Fuel price cannot be negative.")
        }

        val target = dao.getById(id) ?: return@withContext ValidationResult.Error("Record not found.")
        val allRecords = dao.getAllChronologicalList()

        // Check chronological consistency with adjacent records
        val targetIndex = allRecords.indexOfFirst { it.id == id }
        if (targetIndex > 0) {
            val prev = allRecords[targetIndex - 1]
            if (odometerKm < prev.odometerKm) {
                return@withContext ValidationResult.Error("Odometer reading ($odometerKm km) cannot be less than previous record (${prev.odometerKm} km).")
            }
        }
        if (targetIndex >= 0 && targetIndex < allRecords.size - 1) {
            val next = allRecords[targetIndex + 1]
            if (odometerKm > next.odometerKm) {
                return@withContext ValidationResult.Error("Odometer reading ($odometerKm km) cannot be greater than subsequent record (${next.odometerKm} km).")
            }
        }

        dao.update(
            target.copy(
                odometerKm = odometerKm,
                litersAdded = litersAdded,
                pricePerLiter = pricePerLiter
            )
        )
        ValidationResult.Success
    }

    override suspend fun deleteRecord(id: Long) = withContext(dispatcher) {
        dao.deleteById(id)
        Unit
    }

    override suspend fun deleteAllRecords() = withContext(dispatcher) {
        dao.deleteAll()
        Unit
    }

    override suspend fun getRecordById(id: Long): RefuelingRecord? = withContext(dispatcher) {
        val entity = dao.getById(id) ?: return@withContext null
        val allEntities = dao.getAllChronologicalList()
        val derivedList = engine.computeDerivedRecords(allEntities)
        derivedList.firstOrNull { it.id == entity.id }
    }

    override suspend fun exportDataJson(): String = withContext(dispatcher) {
        val entities = dao.getAllChronologicalList()
        com.johnpaulpatigas.motofuel.data.local.DataExporter.exportToJson(entities)
    }

    override suspend fun importDataJson(jsonStr: String): ValidationResult = withContext(dispatcher) {
        try {
            val imported = com.johnpaulpatigas.motofuel.data.local.DataExporter.importFromJson(jsonStr)
            if (imported.isEmpty()) {
                return@withContext ValidationResult.Error("No valid refueling records found in imported JSON file.")
            }
            dao.deleteAll()
            for (entity in imported) {
                dao.insert(entity)
            }
            ValidationResult.Success
        } catch (e: Exception) {
            ValidationResult.Error("Failed to parse JSON file: ${e.localizedMessage}")
        }
    }

    override suspend fun exportDataCsv(): String = withContext(dispatcher) {
        val entities = dao.getAllChronologicalList()
        com.johnpaulpatigas.motofuel.data.local.DataExporter.exportToCsv(entities)
    }

    override suspend fun importDataCsv(csvStr: String): ValidationResult = withContext(dispatcher) {
        try {
            val imported = com.johnpaulpatigas.motofuel.data.local.DataExporter.importFromCsv(csvStr)
            if (imported.isEmpty()) {
                return@withContext ValidationResult.Error("No valid refueling records found in imported CSV file.")
            }
            dao.deleteAll()
            for (entity in imported) {
                dao.insert(entity)
            }
            ValidationResult.Success
        } catch (e: Exception) {
            ValidationResult.Error("Failed to parse CSV file: ${e.localizedMessage}")
        }
    }
}
