package com.dev.sillycrypt.di

import android.content.Context
import com.dev.libsillycript.android.AndroidVeracryptMaster
import com.dev.sillycrypt.domain.entities.CreateVolumeState
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import com.dev.sillycrypt.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class VeracryptModule {

    @Provides
    @Singleton
    fun provideVeracryptMaster(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): AndroidVeracryptMaster {
        val timeoutFlow = settingsRepository.appSettings.map {
            it.timeout
        }
        return AndroidVeracryptMaster(context, timeoutFlow)
    }

    @Provides
    @Singleton
    fun provideVolumeSelectionStateFlow(): MutableStateFlow<VolumeOpeningState> =
        MutableStateFlow(VolumeOpeningState.Initial())

    @Provides
    @Singleton
    fun provideCreateVolumeStateFlow(): MutableStateFlow<CreateVolumeState> =
        MutableStateFlow(CreateVolumeState.Initial)
}