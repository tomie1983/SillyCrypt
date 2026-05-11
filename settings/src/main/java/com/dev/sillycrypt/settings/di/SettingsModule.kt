package com.dev.sillycrypt.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.dev.sillycrypt.settings.data.entities.AppSettingsData
import com.dev.sillycrypt.settings.data.mappers.AppSettingsMapper
import com.dev.sillycrypt.settings.data.repository.SettingsRepositoryImpl
import com.dev.sillycrypt.settings.data.serializers.AppSettingsDataSerializer
import com.dev.sillycrypt.settings.domain.entities.AppSettings
import com.dev.sillycrypt.settings.domain.entities.PackagesAndTimeoutSettings
import com.dev.sillycrypt.settings.domain.repository.SettingsRepository
import com.sillycrypt.mapper.Mapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsMapper(mapper: AppSettingsMapper): Mapper<AppSettingsData, PackagesAndTimeoutSettings>

    companion object {

        @Provides
        @Singleton
        fun provideAppInstalledSharedFlow(): MutableSharedFlow<Boolean> = MutableSharedFlow(1)

        private const val SETTINGS = "settings.json"

        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
            serializer: AppSettingsDataSerializer
        ): DataStore<AppSettingsData> {
            return DataStoreFactory.create(
                serializer,
                produceFile = {
                    File(context.filesDir, SETTINGS)
                }
            )
        }
    }
}