package com.johnpaulpatigas.motofuel.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local persistent storage of refueling records.
 */
@Dao
interface RefuelingDao {

    @Query("SELECT * FROM refueling_records ORDER BY timestamp ASC, odometerKm ASC")
    fun getAllChronological(): Flow<List<RefuelingEntity>>

    @Query("SELECT * FROM refueling_records ORDER BY timestamp ASC, odometerKm ASC")
    suspend fun getAllChronologicalList(): List<RefuelingEntity>

    @Query("SELECT * FROM refueling_records WHERE id = :id")
    suspend fun getById(id: Long): RefuelingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RefuelingEntity): Long

    @Update
    suspend fun update(entity: RefuelingEntity): Int

    @Delete
    suspend fun delete(entity: RefuelingEntity): Int

    @Query("DELETE FROM refueling_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM refueling_records")
    suspend fun deleteAll(): Int
}
