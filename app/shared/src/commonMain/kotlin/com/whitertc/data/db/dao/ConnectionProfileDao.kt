package com.whitertc.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import com.whitertc.data.db.entity.ConnectionProfileEntity

@Dao
interface ConnectionProfileDao {

    @Query("SELECT * FROM connection_profiles ORDER BY isActive DESC, name ASC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles ORDER BY isActive DESC, name ASC")
    suspend fun getAll(): List<ConnectionProfileEntity>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: String): ConnectionProfileEntity?

    @Query("SELECT * FROM connection_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ConnectionProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: ConnectionProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ConnectionProfileEntity>)

    @Query("UPDATE connection_profiles SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(activeId: String)

    @Query("DELETE FROM connection_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
