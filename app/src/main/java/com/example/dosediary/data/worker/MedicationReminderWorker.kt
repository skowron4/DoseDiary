package com.example.dosediary.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.usecase.HandleIntakeDueUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The inexact path of a reminder (used when exact alarms are not allowed): runs when a queued one-time
 * job is due and hands over to [HandleIntakeDueUseCase], which notifies and queues the next one.
 *
 * Also runs jobs queued by earlier app versions; those carry the same keys, and are cancelled by the
 * start-up sync, which replaces them with the new chain.
 */
class MedicationReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val medicationId = inputData.getString(HybridReminderScheduler.EXTRA_MEDICATION_ID)
            ?: return Result.failure()
        val hour = inputData.getInt(HybridReminderScheduler.EXTRA_HOUR, -1)
        val minute = inputData.getInt(HybridReminderScheduler.EXTRA_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) return Result.failure()

        get<HandleIntakeDueUseCase>()(medicationId, ReminderTime(hour, minute))
        return Result.success()
    }
}
