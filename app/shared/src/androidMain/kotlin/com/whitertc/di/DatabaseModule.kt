package com.whitertc.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
    fun provideAppDatabase(context: Context): AppDatabase {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath("whitertc.db")
        return Room.databaseBuilder<AppDatabase>(
            context = appContext,
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
