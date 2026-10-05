package com.example.dosediary

import android.app.Application
import com.example.dosediary.data.worker.ReminderNotifications
import com.example.dosediary.di.appModule
import com.example.dosediary.di.databaseModule
import com.example.dosediary.di.networkModule
import com.example.dosediary.domain.usecase.SyncRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DoseDiaryApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@DoseDiaryApp)
            modules(appModule, networkModule, databaseModule)
        }.koin
        ReminderNotifications.createChannel(this)

        // Re-creates reminders from the stored medications: moves reminders queued by an older app
        // version onto the new schedule and repairs anything the system dropped.
        appScope.launch {
            try {
                koin.get<SyncRemindersUseCase>()()
            } catch (_: Exception) {
                // Never let a scheduling problem crash start-up; the next sync event retries.
            }
        }
    }
}
