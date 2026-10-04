package com.example.dosediary.data.worker

import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.repository.ReminderScheduler
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules one unique 24h periodic job per medication. The first run is delayed until the next
 * occurrence of the requested wall-clock time.
 *
 * WorkManager is deliberately inexact (Doze, battery optimisations), which is acceptable for a
 * non-critical reminder and avoids requiring the exact-alarm permission.
 */
class WorkManagerReminderScheduler(
    private val workManager: WorkManager,
) : ReminderScheduler {

    override fun schedule(medicationId: String, medicationName: String, time: ReminderTime) {
        val request = PeriodicWorkRequestBuilder<MedicationReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNext(time).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(MedicationReminderWorker.KEY_MEDICATION_ID, medicationId)
                    .putString(MedicationReminderWorker.KEY_MEDICATION_NAME, medicationName)
                    .build(),
            )
            .addTag(TAG)
            .build()

        // UPDATE replaces the schedule if the user changes the time, without cancelling a running job.
        workManager.enqueueUniquePeriodicWork(uniqueName(medicationId), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel(medicationId: String) {
        workManager.cancelUniqueWork(uniqueName(medicationId))
    }

    private fun delayUntilNext(time: ReminderTime, now: ZonedDateTime = ZonedDateTime.now()): Duration {
        var next = now.with(LocalTime.of(time.hour, time.minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private fun uniqueName(medicationId: String) = "reminder_$medicationId"

    private companion object {
        const val TAG = "medication_reminder"
    }
}
