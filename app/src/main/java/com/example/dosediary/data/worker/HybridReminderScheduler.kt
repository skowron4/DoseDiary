package com.example.dosediary.data.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.repository.ReminderPermissions
import com.example.dosediary.domain.repository.ReminderScheduler
import com.example.dosediary.domain.schedule.IntakeSchedule
import com.example.dosediary.domain.util.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps exactly one pending reminder per notifying intake: the next occurrence according to
 * [IntakeSchedule] (so "every N days" is honoured), chained one after another.
 *
 * - With exact-alarm access the reminder is an `AlarmManager.setExactAndAllowWhileIdle` alarm that
 *   fires on time even in Doze ([ReminderAlarmReceiver]).
 * - Without it the same occurrence is queued as a unique one-time WorkManager job, which is
 *   inexact but needs no special access.
 *
 * Only one of the two is ever pending for an intake. When the exact-alarm access changes, the system
 * broadcast handled by [ReminderSystemEventsReceiver] re-schedules everything, switching mechanisms.
 */
class HybridReminderScheduler(
    private val context: Context,
    private val workManager: WorkManager,
    private val permissions: ReminderPermissions,
    private val clock: Clock,
) : ReminderScheduler {

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(medication: Medication) {
        val now = clock.nowMillis()
        medication.notifyingIntakes.forEach { scheduleIntake(medication, it, now) }
    }

    override fun scheduleIntake(medication: Medication, intake: Intake, afterMillis: Long) {
        if (!intake.notify) {
            cancelIntake(medication.id, intake.time)
            return
        }
        val after = Instant.ofEpochMilli(afterMillis).atZone(ZoneId.systemDefault())
        val triggerAtMillis = IntakeSchedule.nextOccurrence(
            after = after,
            time = intake.time,
            frequencyDays = medication.frequencyDays,
            startEpochDay = medication.frequencyStartEpochDay,
        ).toInstant().toEpochMilli()

        if (permissions.canScheduleExactAlarms() && setExactAlarm(medication.id, intake.time, triggerAtMillis)) {
            workManager.cancelUniqueWork(workName(medication.id, intake.time))
        } else {
            cancelAlarm(medication.id, intake.time)
            enqueueWork(medication.id, intake.time, triggerAtMillis)
        }
    }

    override fun cancel(medication: Medication) {
        medication.intakes.forEach { cancelIntake(medication.id, it.time) }
        // Periodic jobs and the single reminder scheduled by earlier app versions.
        workManager.cancelAllWorkByTag(medicationTag(medication.id))
        workManager.cancelUniqueWork(legacyUniqueName(medication.id))
    }

    private fun cancelIntake(medicationId: String, time: ReminderTime) {
        cancelAlarm(medicationId, time)
        workManager.cancelUniqueWork(workName(medicationId, time))
    }

    /** @return `false` if the system refused (access revoked meanwhile), so the caller can fall back. */
    private fun setExactAlarm(medicationId: String, time: ReminderTime, triggerAtMillis: Long): Boolean = try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            alarmIntent(medicationId, time, PendingIntent.FLAG_UPDATE_CURRENT)!!,
        )
        true
    } catch (_: SecurityException) {
        false
    }

    private fun cancelAlarm(medicationId: String, time: ReminderTime) {
        alarmIntent(medicationId, time, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun alarmIntent(medicationId: String, time: ReminderTime, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_REMINDER_DUE)
            // The data URI makes the intent unique per intake; extras are ignored by PendingIntent matching.
            .setData(
                Uri.Builder().scheme("dosediary").authority("reminder")
                    .appendPath(medicationId).appendPath(time.hour.toString()).appendPath(time.minute.toString())
                    .build(),
            )
            .putExtra(EXTRA_MEDICATION_ID, medicationId)
            .putExtra(EXTRA_HOUR, time.hour)
            .putExtra(EXTRA_MINUTE, time.minute)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun enqueueWork(medicationId: String, time: ReminderTime, triggerAtMillis: Long) {
        val delay = (triggerAtMillis - clock.nowMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<MedicationReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(EXTRA_MEDICATION_ID, medicationId)
                    .putInt(EXTRA_HOUR, time.hour)
                    .putInt(EXTRA_MINUTE, time.minute)
                    .build(),
            )
            .addTag(medicationTag(medicationId))
            .build()
        workManager.enqueueUniqueWork(workName(medicationId, time), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(medicationId: String, time: ReminderTime) =
        "reminder_next_${medicationId}_${time.hour}_${time.minute}"

    private fun legacyUniqueName(medicationId: String) = "reminder_$medicationId"

    private fun medicationTag(medicationId: String) = "$TAG:$medicationId"

    companion object {
        private const val TAG = "medication_reminder"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_HOUR = "reminder_hour"
        const val EXTRA_MINUTE = "reminder_minute"
    }
}
