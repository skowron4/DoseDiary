package com.example.dosediary.data.worker

import android.content.Context
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.repository.ReminderNotifier

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    override fun show(medication: Medication, intake: Intake) = ReminderNotifications.showReminder(
        context = context,
        medicationId = medication.id,
        medicationName = medication.displayName,
        doseAmount = intake.doseAmount,
        hour = intake.time.hour,
        minute = intake.time.minute,
    )
}
