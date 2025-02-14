package com.example.lottery.di

import android.content.Context
import androidx.room.Room
import com.example.lottery.data.dao.LotteryDao
import com.example.lottery.data.database.LotteryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LotteryDatabase {
        return Room.databaseBuilder(
            context,
            LotteryDatabase::class.java,
            "lottery.db"
        ).build()
    }

    @Provides
    fun provideLotteryDao(database: LotteryDatabase): LotteryDao {
        return database.lotteryDao()
    }
} 