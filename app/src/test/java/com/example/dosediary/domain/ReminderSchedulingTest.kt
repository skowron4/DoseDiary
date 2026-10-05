package com.example.dosediary.domain

import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.usecase.HandleIntakeDueUseCase
import com.example.dosediary.domain.usecase.SyncRemindersUseCase
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleIntakeDueUseCaseTest {

    private val nowMillis = 1_000_000L
    private val morning = Intake(ReminderTime(8, 0), "2 pills", notify = true)
    private val evening = Intake(ReminderTime(20, 0), "1 pill", notify = false)
    private val medication = Medication(
        id = "m1",
        commercialName = "Advil",
        customUserNickname = "Morning pill",
        frequencyDays = 2,
        frequencyStartEpochDay = 20_000,
        intakes = listOf(morning, evening),
    )

    private val scheduler = FakeReminderScheduler()
    private val notifier = FakeReminderNotifier()

    private fun useCase(repository: FakeMedicationRepository) =
        HandleIntakeDueUseCase(repository, scheduler, notifier, Clock { nowMillis })

    @Test
    fun `a due notifying intake shows its notification`() = runTest {
        useCase(FakeMedicationRepository(listOf(medication)))("m1", morning.time)

        assertEquals(listOf(FakeReminderNotifier.Shown("m1", morning)), notifier.shown)
    }

    @Test
    fun `the next reminder of that intake is scheduled after a guard, so an early alarm cannot repeat`() = runTest {
        useCase(FakeMedicationRepository(listOf(medication)))("m1", morning.time)

        assertEquals(
            listOf(
                FakeReminderScheduler.IntakeCall(
                    medicationId = "m1",
                    intake = morning,
                    afterMillis = nowMillis + HandleIntakeDueUseCase.EARLY_FIRE_GUARD_MILLIS,
                ),
            ),
            scheduler.scheduledIntakes,
        )
    }

    @Test
    fun `an intake whose notification was switched off stays silent and ends its chain`() = runTest {
        useCase(FakeMedicationRepository(listOf(medication)))("m1", evening.time)

        assertTrue(notifier.shown.isEmpty())
        assertTrue(scheduler.scheduledIntakes.isEmpty())
    }

    @Test
    fun `a deleted medication stays silent and ends its chain`() = runTest {
        useCase(FakeMedicationRepository())("m1", morning.time)

        assertTrue(notifier.shown.isEmpty())
        assertTrue(scheduler.scheduledIntakes.isEmpty())
    }

    @Test
    fun `an intake that was removed or retimed meanwhile stays silent`() = runTest {
        useCase(FakeMedicationRepository(listOf(medication)))("m1", ReminderTime(9, 15))

        assertTrue(notifier.shown.isEmpty())
        assertTrue(scheduler.scheduledIntakes.isEmpty())
    }
}

class SyncRemindersUseCaseTest {

    private val a = Medication(
        id = "a",
        commercialName = "A",
        intakes = listOf(Intake(ReminderTime(8, 0), notify = true), Intake(ReminderTime(9, 0), notify = false)),
    )
    private val b = Medication(id = "b", commercialName = "B")

    @Test
    fun `every stored medication is cancelled and scheduled again`() = runTest {
        val scheduler = FakeReminderScheduler()

        SyncRemindersUseCase(FakeMedicationRepository(listOf(a, b)), scheduler)()

        assertEquals(listOf(a, b), scheduler.cancelled)
        assertEquals(listOf(a, b), scheduler.scheduled)
    }

    @Test
    fun `an empty diary schedules nothing`() = runTest {
        val scheduler = FakeReminderScheduler()

        SyncRemindersUseCase(FakeMedicationRepository(), scheduler)()

        assertTrue(scheduler.cancelled.isEmpty() && scheduler.scheduled.isEmpty())
    }
}
