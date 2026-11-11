package com.accident.detection.center.di

import android.content.Context
import androidx.room.Room
import com.accident.detection.center.data.local.AppDatabase
import com.accident.detection.center.data.local.dao.CrashEventLogDao
import com.accident.detection.center.data.local.dao.EmergencyContactDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideEmergencyContactDao(database: AppDatabase): EmergencyContactDao {
        return database.emergencyContactDao()
    }

    @Provides
    fun provideCrashEventLogDao(database: AppDatabase): CrashEventLogDao {
        return database.crashEventLogDao()
    }
}

