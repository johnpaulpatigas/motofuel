package com.johnpaulpatigas.motofuel.repository

import com.johnpaulpatigas.motofuel.data.local.RefuelingDao
import com.johnpaulpatigas.motofuel.data.local.RefuelingEntity
import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepositoryImpl
import com.johnpaulpatigas.motofuel.data.repository.ValidationResult
import com.johnpaulpatigas.motofuel.engine.EstimationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeRefuelingDao : RefuelingDao {
    private val records = mutableListOf<RefuelingEntity>()
    private val flow = MutableStateFlow<List<RefuelingEntity>>(emptyList())
    private var idCounter = 1L

    private fun emit() {
        flow.value = records.sortedWith(compareBy<RefuelingEntity> { it.odometerKm }.thenBy { it.timestamp })
    }

    override fun getAllChronological(): Flow<List<RefuelingEntity>> = flow

    override suspend fun getAllChronologicalList(): List<RefuelingEntity> {
        return records.sortedWith(compareBy<RefuelingEntity> { it.odometerKm }.thenBy { it.timestamp })
    }

    override suspend fun getById(id: Long): RefuelingEntity? {
        return records.find { it.id == id }
    }

    override suspend fun insert(entity: RefuelingEntity): Long {
        val newId = if (entity.id == 0L) idCounter++ else entity.id
        val newEntity = entity.copy(id = newId)
        records.add(newEntity)
        emit()
        return newId
    }

    override suspend fun update(entity: RefuelingEntity): Int {
        val index = records.indexOfFirst { it.id == entity.id }
        return if (index >= 0) {
            records[index] = entity
            emit()
            1
        } else 0
    }

    override suspend fun delete(entity: RefuelingEntity): Int {
        val removed = records.removeAll { it.id == entity.id }
        if (removed) emit()
        return if (removed) 1 else 0
    }

    override suspend fun deleteById(id: Long): Int {
        val removed = records.removeAll { it.id == id }
        if (removed) emit()
        return if (removed) 1 else 0
    }

    override suspend fun deleteAll(): Int {
        val count = records.size
        records.clear()
        emit()
        return count
    }
}

class RefuelingRepositoryTest {

    private lateinit var dao: FakeRefuelingDao
    private lateinit var repository: RefuelingRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeRefuelingDao()
        repository = RefuelingRepositoryImpl(dao, EstimationEngine())
    }

    @Test
    fun `validateAndAdd - rejects zero or negative odometer`() = runBlocking {
        val res1 = repository.validateAndAdd(odometerKm = 0.0, litersAdded = 10.0)
        assertTrue(res1 is ValidationResult.Error)

        val res2 = repository.validateAndAdd(odometerKm = -100.0, litersAdded = 10.0)
        assertTrue(res2 is ValidationResult.Error)
    }

    @Test
    fun `validateAndAdd - rejects zero or negative fuel`() = runBlocking {
        val res1 = repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 0.0)
        assertTrue(res1 is ValidationResult.Error)

        val res2 = repository.validateAndAdd(odometerKm = 1000.0, litersAdded = -5.0)
        assertTrue(res2 is ValidationResult.Error)
    }

    @Test
    fun `validateAndAdd - rejects non-increasing odometer`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)

        // Adding lower odometer reading than 1000.0
        val result = repository.validateAndAdd(odometerKm = 950.0, litersAdded = 5.0)
        assertTrue(result is ValidationResult.Error)
        assertEquals(
            "Odometer reading (950.0 km) cannot be less than the previous recorded odometer reading (1000.0 km).",
            (result as ValidationResult.Error).message
        )
    }

    @Test
    fun `validateAndAdd - accepts valid consecutive logs and recalculates estimates`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0) // Baseline
        repository.validateAndAdd(odometerKm = 1250.0, litersAdded = 8.0)  // +250 km, 8 L

        val records = repository.getDerivedRecords().first()
        assertEquals(2, records.size)
        assertEquals(250.0, records[1].intervalKm!!, 0.001)

        val estimation = repository.getEstimationResult().first()
        assertEquals(250.0, estimation.totalDistanceKm, 0.001)
        assertEquals(8.0, estimation.totalFuelConsumedLiters, 0.001)
        assertEquals(31.25, estimation.cumulativeKmLiters!!, 0.001)
    }

    @Test
    fun `record edits and deletions recalculate model state`() = runBlocking {
        repository.validateAndAdd(odometerKm = 1000.0, litersAdded = 10.0)
        val res2 = repository.validateAndAdd(odometerKm = 1200.0, litersAdded = 5.0)
        assertTrue(res2 is ValidationResult.Success)

        val recordsBefore = repository.getDerivedRecords().first()
        val record2Id = recordsBefore[1].id

        // Delete record 2
        repository.deleteRecord(record2Id)

        val recordsAfter = repository.getDerivedRecords().first()
        assertEquals(1, recordsAfter.size)
        assertTrue(recordsAfter[0].isBaseline)

        val estimationAfter = repository.getEstimationResult().first()
        assertEquals(0, estimationAfter.intervalCount)
        assertEquals(0.0, estimationAfter.totalDistanceKm, 0.001)
    }
}
