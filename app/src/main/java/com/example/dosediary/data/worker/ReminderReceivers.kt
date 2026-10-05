package com.example.dosediary.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.usecase.HandleIntakeDueUseCase
import com.example.dosediary.domain.usecase.SyncRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Runs [block] off the main thread while keeping the receiver's process alive until it is done. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (_: Exception) {
            // A failed reminder must never crash the app; the next sync re-creates the schedule.
        } finally {
            pending.finish()
        }
    }
}

/** Fires when an exact alarm of an intake goes off. */
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(HybridReminderScheduler.EXTRA_MEDICATION_ID) ?: return
        val hour = intent.getIntExtra(HybridReminderScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(HybridReminderScheduler.EXTRA_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) return

        runAsync { get<HandleIntakeDueUseCase>()(medicationId, ReminderTime(hour, minute)) }
    }

    companion object {
        const val ACTION_REMINDER_DUE = "com.example.dosediary.action.REMINDER_DUE"
    }
}

/**
 * Re-creates every reminder after something dropped or invalidated them: device reboot, app update,
 * time-zone change, or the user granting/revoking exact-alarm access.
 */
class ReminderSystemEventsReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        runAsync { get<SyncRemindersUseCase>()() }
    }

    private companion object {
        /** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+). */
        const val EXACT_ALARM_STATE_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            EXACT_ALARM_STATE_CHANGED,
        )
    }
}
