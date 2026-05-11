package com.sillycrypt.exfat_browser.di

import com.dev.exfat.exfat.VeracryptVolumeData
import com.sillycrypt.exfat_browser.data.ExFATBrowserRepositoryImpl
import com.sillycrypt.exfat_browser.domain.entities.BrowserDisplayEntry
import com.sillycrypt.exfat_browser.domain.repository.ExFATBrowserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EXFATBrowserModule {

    @Binds
    @Singleton
    abstract fun bindExFATBrowserRepository(impl: ExFATBrowserRepositoryImpl): ExFATBrowserRepository

    companion object {
        @Provides
        @Singleton
        fun provideVeracryptVolumeDataSharedFlow(): MutableSharedFlow<VeracryptVolumeData> =
            MutableSharedFlow(replay = 1)

        @Provides
        @Singleton
        fun provideBrowserEntryFlow(): MutableSharedFlow<BrowserDisplayEntry> = MutableSharedFlow(replay = 1)
    }
}