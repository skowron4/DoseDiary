package com.example.dosediary.data.worker

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.dosediary.domain.repository.ReminderPermissions

/** [ReminderPermissions] backed by the real Android permission and system-service state. */
class AndroidReminderPermissions(private val context: Context) : ReminderPermissions {

    // Also false when the user switched notifications off for the app in system settings.
    override fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun canRequestNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    override fun exactAlarmsNeedUserAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
