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
 * Schedules one unique 24h periodic job per medication *and* reminder time, so a medication can have
 * several daily reminders. The first run of each job is delayed until the next occurrence of its
 * wall-clock time.
 *
 * WorkManager is deliberately inexact (Doze, battery optimisations), which is acceptable for a
 * non-critical reminder and avoids requiring the exact-alarm permission.
 */
class WorkManagerReminderScheduler(
    private val workManager: WorkManager,
) : ReminderScheduler {

    override fun schedule(medicationId: String, medicationName: String, times: List<ReminderTime>) {
        // Drop the previous set first so removed times stop firing.
        cancel(medicationId)

        times.distinct().forEach { time ->
            val request = PeriodicWorkRequestBuilder<MedicationReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNext(time).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(MedicationReminderWorker.KEY_MEDICATION_ID, medicationId)
                        .putString(MedicationReminderWorker.KEY_MEDICATION_NAME, medicationName)
                        .putInt(MedicationReminderWorker.KEY_HOUR, time.hour)
                        .putInt(MedicationReminderWorker.KEY_MINUTE, time.minute)
                        .build(),
                )
                .addTag(TAG)
                .addTag(medicationTag(medicationId))
                .build()

            workManager.enqueueUniquePeriodicWork(
                uniqueName(medicationId, time),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    override fun cancel(medicationId: String) {
        workManager.cancelAllWorkByTag(medicationTag(medicationId))
        // Single daily reminder scheduled by app versions before multiple reminders existed.
        workManager.cancelUniqueWork(legacyUniqueName(medicationId))
    }

    private fun delayUntilNext(time: ReminderTime, now: ZonedDateTime = ZonedDateTime.now()): Duration {
        var next = now.with(LocalTime.of(time.hour, time.minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private fun uniqueName(medicationId: String, time: ReminderTime) =
        "reminder_${medicationId}_${time.hour}_${time.minute}"

    private fun legacyUniqueName(medicationId: String) = "reminder_$medicationId"

    private fun medicationTag(medicationId: String) = "$TAG:$medicationId"

    private companion object {
        const val TAG = "medication_reminder"
    }
}