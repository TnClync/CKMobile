package com.tdtu.ckmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tdtu.ckmobile.data.local.entity.ConflictLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: ConflictLogEntity): Long

    @Query("SELECT * FROM conflict_log ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<ConflictLogEntity>>

    @Query("SELECT * FROM conflict_log ORDER BY detectedAt DESC LIMIT 1")
    suspend fun getLatest(): ConflictLogEntity?
}
