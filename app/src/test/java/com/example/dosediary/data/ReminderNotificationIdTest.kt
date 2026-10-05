package com.example.dosediary.data

import com.example.dosediary.data.worker.ReminderNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderNotificationIdTest {

    @Test
    fun `two reminder times of the same medication get different ids`() {
        val morning = ReminderNotifications.notificationId("m1", 8, 0)
        val afternoon = ReminderNotifications.notificationId("m1", 14, 30)
        assertNotEquals(morning, afternoon)
    }

    @Test
    fun `the same medication and time always give the same id`() {
        assertEquals(
            ReminderNotifications.notificationId("m1", 8, 0),
            ReminderNotifications.notificationId("m1", 8, 0),
        )
    }

    @Test
    fun `different medications at the same time get different ids`() {
        assertNotEquals(
            ReminderNotifications.notificationId("m1", 8, 0),
            ReminderNotifications.notificationId("m2", 8, 0),
        )
    }

    @Test
    fun `minutes are part of the id`() {
        assertNotEquals(
            ReminderNotifications.notificationId("m1", 8, 0),
            ReminderNotifications.notificationId("m1", 8, 1),
        )
    }
}
