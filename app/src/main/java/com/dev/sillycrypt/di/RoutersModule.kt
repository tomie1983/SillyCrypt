package com.dev.sillycrypt.di

import com.dev.sillycrypt.routers.WorkProfileRouterImpl
import com.libsillycrypt.workprofile.domain.router.WorkProfileRouter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RoutersModule {

    @Binds
    @Singleton
    abstract fun provideWorkProfileRouter(impl: WorkProfileRouterImpl): WorkProfileRouter
}