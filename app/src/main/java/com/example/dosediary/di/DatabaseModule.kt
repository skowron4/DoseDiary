package com.example.dosediary.di

import androidx.room.Room
import com.example.dosediary.data.local.AppDatabase
import com.example.dosediary.data.local.MIGRATION_3_4
import com.example.dosediary.data.local.MIGRATION_4_5
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> {
        // No destructive-migration fallback: this is user health data, so schema changes must ship
        // with real migrations.
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }
    single { get<AppDatabase>().medicationDao() }
    single { get<AppDatabase>().symptomDao() }
}
