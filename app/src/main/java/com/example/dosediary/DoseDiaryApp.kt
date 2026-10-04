package com.example.dosediary

import android.app.Application
import com.example.dosediary.data.worker.ReminderNotifications
import com.example.dosediary.di.appModule
import com.example.dosediary.di.databaseModule
import com.example.dosediary.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DoseDiaryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@DoseDiaryApp)
            modules(appModule, networkModule, databaseModule)
        }
        ReminderNotifications.createChannel(this)
    }
}
