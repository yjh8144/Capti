package com.capti.di

import android.content.Context
import com.capti.audio.AudioCaptureManager
import com.capti.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepository(context)

    @Provides
    @Singleton
    fun provideAudioCaptureManager(
        @ApplicationContext context: Context
    ): AudioCaptureManager = AudioCaptureManager(context)
}
