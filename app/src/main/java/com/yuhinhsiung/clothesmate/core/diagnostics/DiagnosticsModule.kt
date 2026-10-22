package com.yuhinhsiung.clothesmate.core.diagnostics

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {
    @Provides
    @Singleton
    fun provideCrashLogStore(@ApplicationContext context: Context): CrashLogStore =
        CrashLogStore(CrashLogStore.defaultDirectory(context.filesDir))
}
