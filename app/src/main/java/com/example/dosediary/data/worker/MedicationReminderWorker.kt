package com.example.dosediary.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Fires a local notification for a medication. Scheduled as a 24h periodic job by
 * [WorkManagerReminderScheduler]; all data it needs is passed via input data, so the worker has no
 * dependency on the database or DI graph.
 */
class MedicationReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val medicationId = inputData.getString(KEY_MEDICATION_ID) ?: return Result.failure()
        val medicationName = inputData.getString(KEY_MEDICATION_NAME) ?: return Result.failure()
        // Jobs scheduled before multiple reminders existed carry no time; -1 keeps them working.
        val hour = inputData.getInt(KEY_HOUR, NO_TIME)
        val minute = inputData.getInt(KEY_MINUTE, NO_TIME)
        ReminderNotifications.showReminder(applicationContext, medicationId, medicationName, hour, minute)
        return Result.success()
    }

    companion object {
        const val KEY_MEDICATION_ID = "medication_id"
        const val KEY_MEDICATION_NAME = "medication_name"
        const val KEY_HOUR = "reminder_hour"
        const val KEY_MINUTE = "reminder_minute"
        private const val NO_TIME = -1
    }
}
