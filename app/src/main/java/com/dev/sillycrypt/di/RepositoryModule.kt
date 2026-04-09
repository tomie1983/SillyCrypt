package com.dev.sillycrypt.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.dev.sillycrypt.data.repository.AppSettingsRepositoryImpl
import com.dev.sillycrypt.data.repository.CreateVolumeRepositoryImpl
import com.dev.sillycrypt.data.repository.ManageVolumeRepositoryImpl
import com.dev.sillycrypt.data.serializers.AppSettingsSerializer
import com.dev.sillycrypt.domain.entities.AppSettings
import com.dev.sillycrypt.domain.repository.CreateVolumeRepository
import com.dev.sillycrypt.domain.repository.ManageVolumeRepository
import com.dev.sillycrypt.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOpenVolumeRepository(
        impl: ManageVolumeRepositoryImpl
    ): ManageVolumeRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: AppSettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCreateVolumeRepository(
        impl: CreateVolumeRepositoryImpl
    ): CreateVolumeRepository

    companion object {

        private const val SETTINGS_FILE = "app_settings.json"

        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context
        ): DataStore<AppSettings> {
            return DataStoreFactory.create(
                serializer = AppSettingsSerializer,
                produceFile = {
                    File(context.filesDir, SETTINGS_FILE)
                }
            )
        }
    }
}