package com.yuhinhsiung.clothesmate.feature.today.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearDataModule {

    @Binds
    @Singleton
    abstract fun bindWearRepository(impl: DefaultWearRepository): WearRepository

    @Binds
    @Singleton
    abstract fun bindScheduleSelectionStore(
        impl: DataStoreScheduleSelectionStore,
    ): ScheduleSelectionStore

    @Binds
    @Singleton
    abstract fun bindWearService(impl: DefaultWearService): WearService
}
