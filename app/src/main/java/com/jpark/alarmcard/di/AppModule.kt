package com.jpark.alarmcard.di

import android.content.Context
import androidx.room.Room
import com.jpark.alarmcard.data.local.AppDatabase
import com.jpark.alarmcard.data.local.CardDao
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
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "alarmcard.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCardDao(db: AppDatabase): CardDao = db.cardDao()
}
