package com.whitertc.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers
import com.whitertc.data.db.AppDatabase
import com.whitertc.data.db.dao.ConnectionProfileDao
import com.whitertc.data.db.dao.VpnConfigDao
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
class DatabaseModule {

    @Single
    fun provideAppDatabase(): AppDatabase {
        val userHome = System.getProperty("user.home") ?: "."
        val newDir = File(userHome, ".whitertc")
        val oldDir = File(userHome, ".whiteproxy")
        val dir = if (!newDir.exists() && oldDir.exists()) oldDir else newDir.apply { mkdirs() }
        val oldDb = File(dir, "white_proxy.db")
        val dbFile = if (!File(dir, "whitertc.db").exists() && oldDb.exists()) oldDb else File(dir, "whitertc.db")
        return Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @Single
    fun provideVpnConfigDao(database: AppDatabase): VpnConfigDao = database.vpnConfigDao()

    @Single
    fun provideConnectionProfileDao(database: AppDatabase): ConnectionProfileDao = database.connectionProfileDao()
}
