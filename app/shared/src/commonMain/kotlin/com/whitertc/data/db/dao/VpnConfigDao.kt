package com.whitertc.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import com.whitertc.data.db.entity.VpnConfigEntity

@Dao
interface VpnConfigDao {

    @Query("SELECT * FROM vpn_config WHERE id = :id")
    suspend fun getById(id: String): VpnConfigEntity?

    @Query("SELECT * FROM vpn_config WHERE id = :id")
    fun observeById(id: String): Flow<VpnConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: VpnConfigEntity)
}
