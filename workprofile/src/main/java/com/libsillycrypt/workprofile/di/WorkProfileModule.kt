package com.libsillycrypt.workprofile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.libsillycrypt.core.serialization.BaseSerializer
import com.libsillycrypt.workprofile.data.repository.WorkProfileRepositoryImpl
import com.libsillycrypt.workprofile.data.serializer.AuthDataSerializer
import com.libsillycrypt.workprofile.data.serializer.WorkProfileSettingsSerializer
import com.libsillycrypt.workprofile.domain.entities.AuthData
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
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
abstract class WorkProfileModule {

    @Binds
    @Singleton
    abstract fun bindWorkProfileRepository(impl: WorkProfileRepositoryImpl): WorkProfileRepository

    companion object {

        private const val WORK_PROFILE_SETTINGS = "app_settings.json"
        private const val AUTH_DATA_SETTINGS = "auth_data_settings.json"

        @Provides
        @Singleton
        fun provideSettingsSerializer(): BaseSerializer<WorkProfileSettings> =
            WorkProfileSettingsSerializer()

        @Provides
        @Singleton
        fun provideWorkProfileStatusFlow(): MutableSharedFlow<Boolean> = MutableSharedFlow()

        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
            serializer: BaseSerializer<WorkProfileSettings>
        ): DataStore<WorkProfileSettings> {
            return DataStoreFactory.create(
                serializer = serializer,
                produceFile = {
                    File(context.filesDir, WORK_PROFILE_SETTINGS)
                }
            )
        }

        @Provides
        @Singleton
        fun provideAuthDataSerializer(): BaseSerializer<AuthData> = AuthDataSerializer()

        @Provides
        @Singleton
        fun provideAuthDataStore(
            @ApplicationContext context: Context,
            serializer: AuthDataSerializer
        ): DataStore<AuthData> {
            return DataStoreFactory.create(
                serializer,
                produceFile = {
                    File(context.filesDir, AUTH_DATA_SETTINGS)
                }
            )
        }
    }
}