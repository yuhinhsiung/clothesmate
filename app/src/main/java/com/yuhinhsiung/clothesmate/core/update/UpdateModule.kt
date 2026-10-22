package com.yuhinhsiung.clothesmate.core.update

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object UpdateProvidesModule {
    @Provides
    @Singleton
    fun provideGitHubReleaseApi(retrofit: Retrofit): GitHubReleaseApi =
        retrofit.newBuilder()
            .baseUrl("https://api.github.com/")
            .build()
            .create(GitHubReleaseApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindModule {
    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: DefaultUpdateRepository): UpdateRepository
}
