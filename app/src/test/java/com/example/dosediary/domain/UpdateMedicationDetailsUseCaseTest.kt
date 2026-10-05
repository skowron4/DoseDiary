package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.usecase.UpdateMedicationDetailsUseCase
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UpdateMedicationDetailsUseCaseTest {
    private val medication = Medication(id = "m1", commercialName = "Advil")

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock {
        LocalDateTime.of(today, java.time.LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private val morning = Intake(ReminderTime(8, 0), "2 pills", notify = true)
    private val evening = Intake(ReminderTime(20, 0), "1 pill", notify = false)

    private suspend fun update(
        repository: FakeMedicationRepository,
        scheduler: FakeReminderScheduler = FakeReminderScheduler(),
        id: String = "m1",
        nickname: String = "",
        frequencyDays: Int = 1,
        intakes: List<Intake> = emptyList(),
    ) = UpdateMedicationDetailsUseCase(repository, scheduler, clock)(id, nickname, frequencyDays, intakes)

    @Test
    fun `text is trimmed and everything is stored with intakes sorted by time`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))

        val result = update(
            repository,
            nickname = "  Morning pill ",
            frequencyDays = 3,
            intakes = listOf(evening.copy(doseAmount = " 1 pill "), morning),
        )

        assertEquals(AppResult.Success(Unit), result)
        val saved = repository.getMedication("m1")!!
        assertEquals("Morning pill", saved.customUserNickname)
        assertEquals(3, saved.frequencyDays)
        assertEquals(listOf(morning, evening), saved.intakes)
    }

    @Test
    fun `blank nickname and blank doses are cleared`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication.copy(customUserNickname = "Old")))

        update(repository, nickname = "   ", intakes = listOf(morning.copy(doseAmount = "  ")))

        val saved = repository.getMedication("m1")!!
        assertNull(saved.customUserNickname)
        assertNull(saved.intakes.single().doseAmount)
    }

    @Test
    fun `invalid input is rejected, nothing changes and nothing is scheduled`() = runTest {
        suspend fun assertRejected(reason: ValidationReason, run: suspend (FakeMedicationRepository, FakeReminderScheduler) -> AppResult<Unit>) {
            val repository = FakeMedicationRepository(listOf(medication))
            val scheduler = FakeReminderScheduler()
            assertEquals(AppResult.Failure(DomainError.Validation(reason)), run(repository, scheduler))
            assertEquals(medication, repository.getMedication("m1"))
            assertTrue(scheduler.scheduled.isEmpty() && scheduler.cancelled.isEmpty())
        }

        assertRejected(ValidationReason.NICKNAME_TOO_LONG) { r, s -> update(r, s, nickname = "x".repeat(41)) }
        assertRejected(ValidationReason.DOSE_TOO_LONG) { r, s ->
            update(r, s, intakes = listOf(morning.copy(doseAmount = "x".repeat(41))))
        }
        assertRejected(ValidationReason.FREQUENCY_OUT_OF_RANGE) { r, s -> update(r, s, frequencyDays = 0) }
        assertRejected(ValidationReason.FREQUENCY_OUT_OF_RANGE) { r, s -> update(r, s, frequencyDays = 91) }
        assertRejected(ValidationReason.TOO_MANY_INTAKES) { r, s ->
            update(r, s, intakes = (0 until 11).map { Intake(ReminderTime(it, 0)) })
        }
        assertRejected(ValidationReason.DUPLICATE_INTAKE_TIME) { r, s ->
            update(r, s, intakes = listOf(morning, morning.copy(doseAmount = "other")))
        }
    }

    @Test
    fun `unknown medication is reported`() = runTest {
        val scheduler = FakeReminderScheduler()
        val result = update(FakeMedicationRepository(), scheduler, id = "nope", intakes = listOf(morning))

        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND)),
            result,
        )
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `previous reminders are cancelled with the stored intakes before the new ones are scheduled`() = runTest {
        val stored = medication.copy(intakes = listOf(Intake(ReminderTime(9, 0), notify = true)))
        val repository = FakeMedicationRepository(listOf(stored))
        val scheduler = FakeReminderScheduler()

        update(repository, scheduler, nickname = "Morning pill", intakes = listOf(morning, evening))

        // Cancelled with the old intakes (so the removed 09:00 reminder disappears) ...
        assertEquals(listOf(stored), scheduler.cancelled)
        // ... and scheduled with what was saved, under the new name.
        val scheduled = scheduler.scheduled.single()
        assertEquals("Morning pill", scheduled.displayName)
        assertEquals(listOf(morning, evening), scheduled.intakes)
    }

    @Test
    fun `a switched-off intake is stored but is not a notifying intake`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))
        val scheduler = FakeReminderScheduler()

        update(repository, scheduler, intakes = listOf(morning, evening))

        assertEquals(listOf(morning, evening), repository.getMedication("m1")!!.intakes)
        assertEquals(listOf(morning), scheduler.scheduled.single().notifyingIntakes)
    }

    @Test
    fun `removing every intake leaves nothing to schedule`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication.copy(intakes = listOf(morning))))
        val scheduler = FakeReminderScheduler()

        update(repository, scheduler, intakes = emptyList())

        assertTrue(repository.getMedication("m1")!!.intakes.isEmpty())
        assertEquals(1, scheduler.cancelled.size)
        assertTrue(scheduler.scheduled.single().notifyingIntakes.isEmpty())
    }

    @Test
    fun `a new or changed interval starts counting today`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication.copy(frequencyDays = 1)))

        update(repository, frequencyDays = 3, intakes = listOf(morning))

        assertEquals(today.toEpochDay(), repository.getMedication("m1")!!.frequencyStartEpochDay)

        // Changing 3 -> 5 restarts the count even though a start day exists.
        val older = today.minusDays(10).toEpochDay()
        val changed = FakeMedicationRepository(listOf(medication.copy(frequencyDays = 3, frequencyStartEpochDay = older)))
        update(changed, frequencyDays = 5, intakes = listOf(morning))
        assertEquals(today.toEpochDay(), changed.getMedication("m1")!!.frequencyStartEpochDay)
    }

    @Test
    fun `saving with an unchanged interval keeps the rhythm`() = runTest {
        val older = today.minusDays(10).toEpochDay()
        val repository = FakeMedicationRepository(
            listOf(medication.copy(frequencyDays = 3, frequencyStartEpochDay = older)),
        )

        update(repository, nickname = "Renamed", frequencyDays = 3, intakes = listOf(morning))

        assertEquals(older, repository.getMedication("m1")!!.frequencyStartEpochDay)
    }
}
