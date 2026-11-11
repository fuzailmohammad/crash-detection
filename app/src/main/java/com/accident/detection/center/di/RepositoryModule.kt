package com.accident.detection.center.di

import com.accident.detection.center.data.repository.CrashLogRepositoryImpl
import com.accident.detection.center.data.repository.EmergencyContactRepositoryImpl
import com.accident.detection.center.domain.repository.CrashLogRepository
import com.accident.detection.center.domain.repository.EmergencyContactRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEmergencyContactRepository(
        impl: EmergencyContactRepositoryImpl
    ): EmergencyContactRepository

    @Binds
    @Singleton
    abstract fun bindCrashLogRepository(
        impl: CrashLogRepositoryImpl
    ): CrashLogRepository
}

