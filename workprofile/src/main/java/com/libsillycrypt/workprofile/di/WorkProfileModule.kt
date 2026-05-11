package com.libsillycrypt.workprofile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.libsillycrypt.core.serialization.BaseSerializer
import com.libsillycrypt.workprofile.data.mappers.AppListMapper
import com.libsillycrypt.workprofile.data.repository.WorkProfileRepositoryImpl
import com.libsillycrypt.workprofile.data.serializer.AuthDataSerializer
import com.libsillycrypt.workprofile.data.serializer.WorkProfileSettingsSerializer
import com.libsillycrypt.workprofile.domain.entities.AuthData
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import com.sillycrypt.mapper.Mapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkProfileModule {

    @Binds
    @Singleton
    abstract fun bindWorkProfileRepository(impl: WorkProfileRepositoryImpl): WorkProfileRepository

    companion object {

        private const val WORK_PROFILE_SETTINGS = "workprofile_app_settings.json"
        private const val AUTH_DATA_SETTINGS = "auth_data_settings.json"

        @Provides
        @Singleton
        fun provideSettingsSerializer(): BaseSerializer<WorkProfileSettings> =
            WorkProfileSettingsSerializer()

        @Provides
        @Singleton
        fun provideWorkProfileStatusFlow(): MutableStateFlow<Boolean> = MutableStateFlow(false)

        @Provides
        @Singleton
        fun provideWorkProfileAppsFlow(): MutableStateFlow<List<String>> = MutableStateFlow(
            persistentListOf()
        )

        @Provides
        @Singleton
        fun provideAppListMapper(
            @ApplicationContext context: Context
        ): Mapper<List<String>, ImmutableList<ApplicationInfoWithData>> =
            AppListMapper(context)

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