package com.whitertc.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.whitertc.data.db.dao.ConnectionProfileDao
import com.whitertc.data.db.dao.VpnConfigDao
import com.whitertc.data.db.entity.ConnectionProfileEntity
import com.whitertc.data.db.entity.VpnConfigEntity

@Database(
    entities = [VpnConfigEntity::class, ConnectionProfileEntity::class],
    version = 10,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vpnConfigDao(): VpnConfigDao
    abstract fun connectionProfileDao(): ConnectionProfileDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
