package com.accident.detection.center.di

import android.content.Context
import com.accident.detection.center.sensor.SensorRingBuffer
import com.accident.detection.center.sensor.SignalFilter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideSensorRingBuffer(): SensorRingBuffer {
        return SensorRingBuffer(capacity = 1500)
    }

    @Provides
    @Singleton
    fun provideSignalFilter(): SignalFilter {
        return SignalFilter()
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }
}

