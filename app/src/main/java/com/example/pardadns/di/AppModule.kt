package com.example.pardadns.di

import android.content.Context
import androidx.room.Room
import com.example.pardadns.data.db.PardaDatabase
import com.example.pardadns.data.db.StatsDao
import com.example.pardadns.data.repository.DnsRepository
import com.example.pardadns.data.repository.DnsRepositoryImpl
import com.example.pardadns.data.repository.StatsRepository
import com.example.pardadns.data.repository.StatsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDnsRepository(impl: DnsRepositoryImpl): DnsRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext ctx: Context): PardaDatabase =
            Room.databaseBuilder(ctx, PardaDatabase::class.java, "parda_stats.db")
                .fallbackToDestructiveMigration()
                .build()

        @Provides
        @Singleton
        fun provideStatsDao(db: PardaDatabase): StatsDao = db.statsDao()
    }
}
