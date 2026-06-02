package com.dev.sillycrypt.di

import com.dev.sillycrypt.presentation.states.ErrorState
import com.dev.sillycrypt.presentation.states.OpenVolumeUIState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
class OpenVolumeScreenModule {
    @Provides
    @ViewModelScoped
    fun provideOpenVolumeUiState() = MutableStateFlow<OpenVolumeUIState>(OpenVolumeUIState.Initial())

    @Provides
    @ViewModelScoped
    fun provideErrorState() = MutableStateFlow<ErrorState?>(null)
}