package com.example.dosediary.domain.repository

import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication

/**
 * Platform abstraction for scheduling local medication reminders.
 *
 * Every notifying [Intake] has exactly one pending reminder: the next time it is due according to
 * [com.example.dosediary.domain.schedule.IntakeSchedule]. After a reminder fires the next one is
 * scheduled with [scheduleIntake].
 */
interface ReminderScheduler {
    /** Schedules the next reminder of every notifying intake, replacing the pending one of that intake. */
    fun schedule(medication: Medication)

    /**
     * Schedules the first reminder of a single [intake] strictly after [afterMillis] (epoch millis), or cancels its pending reminder when the intake
     * does not notify.
     */
    fun scheduleIntake(medication: Medication, intake: Intake, afterMillis: Long)

    /** Removes every pending reminder of [medication] (use the stored version, so removed intakes are included). */
    fun cancel(medication: Medication)
}

/** What the OS currently allows regarding reminders. Implemented on top of the Android APIs. */
interface ReminderPermissions {
    /** Notifications can be shown right now (runtime permission granted and not blocked in settings). */
    fun canPostNotifications(): Boolean

    /** The runtime permission dialog can be used to ask for notifications (Android 13+ and not granted). */
    fun canRequestNotificationPermission(): Boolean

    /** This Android version gates exact alarms behind special app access (Android 12+). */
    fun exactAlarmsNeedUserAccess(): Boolean

    /** Exact alarms may be scheduled right now. Always true where no special access is needed. */
    fun canScheduleExactAlarms(): Boolean
}

/** Shows the notification for a due intake. */
interface ReminderNotifier {
    fun show(medication: Medication, intake: Intake)
}
