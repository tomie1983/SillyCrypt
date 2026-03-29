package com.dev.sillycrypt.di

import com.dev.libsillycript.android.AndroidVeracryptMaster
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import com.dev.sillycrypt.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
        settingsRepository: SettingsRepository
    ): AndroidVeracryptMaster {
        val timeoutFlow = settingsRepository.appSettings.map {
            it.timeout
        }
        return AndroidVeracryptMaster(timeoutFlow)
    }

    @Provides
    @Singleton
    fun provideVolumeSelectionStateFlow(): MutableStateFlow<VolumeOpeningState> =
        MutableStateFlow(VolumeOpeningState.Initial())
}