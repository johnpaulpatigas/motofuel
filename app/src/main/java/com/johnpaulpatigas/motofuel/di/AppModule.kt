package com.johnpaulpatigas.motofuel.di

import android.content.Context
import com.johnpaulpatigas.motofuel.data.local.AppDatabase
import com.johnpaulpatigas.motofuel.data.local.PreferencesManager
import com.johnpaulpatigas.motofuel.data.local.RefuelingDao
import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepository
import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepositoryImpl
import com.johnpaulpatigas.motofuel.engine.EstimationEngine
import com.johnpaulpatigas.motofuel.engine.RecursiveLeastSquaresEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideRefuelingDao(database: AppDatabase): RefuelingDao {
        return database.refuelingDao()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideEstimationEngine(): EstimationEngine {
        return EstimationEngine(RecursiveLeastSquaresEngine())
    }

    @Provides
    @Singleton
    fun provideRefuelingRepository(
        refuelingDao: RefuelingDao,
        estimationEngine: EstimationEngine
    ): RefuelingRepository {
        return RefuelingRepositoryImpl(refuelingDao, estimationEngine)
    }
}
