package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.model.matchesQuery
import com.example.dosediary.domain.usecase.UpdateMedicationDetailsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationModelTest {

    private val advil = Medication(
        id = "1",
        commercialName = "Advil",
        activeSubstance = "Ibuprofen",
        manufacturer = "Haleon",
    )

    @Test
    fun `display name is the commercial name when there is no nickname`() {
        assertEquals("Advil", advil.displayName)
        assertEquals("Ibuprofen", advil.displaySubtitle)
    }

    @Test
    fun `display name prefers the nickname and keeps the active substance as subtitle`() {
        val nicknamed = advil.copy(customUserNickname = "Morning pill")
        assertEquals("Morning pill", nicknamed.displayName)
        assertEquals("Ibuprofen", nicknamed.displaySubtitle)
    }

    @Test
    fun `blank nickname and blank substance are ignored`() {
        val blank = advil.copy(customUserNickname = "   ", activeSubstance = " ")
        assertEquals("Advil", blank.displayName)
        assertNull(blank.displaySubtitle)
    }

    @Test
    fun `query matches nickname, commercial name, substance and manufacturer ignoring case`() {
        val nicknamed = advil.copy(customUserNickname = "Morning pill")
        assertTrue(nicknamed.matchesQuery("morning"))
        assertTrue(nicknamed.matchesQuery("ADV"))
        assertTrue(nicknamed.matchesQuery("ibupro"))
        assertTrue(nicknamed.matchesQuery("hale"))
        assertFalse(nicknamed.matchesQuery("aspirin"))
    }

    @Test
    fun `blank query matches everything`() {
        assertTrue(advil.matchesQuery(""))
        assertTrue(advil.matchesQuery("   "))
    }
}

class UpdateMedicationDetailsUseCaseTest {
    private val medication = Medication(id = "m1", commercialName = "Advil")

    private suspend fun update(
        repository: FakeMedicationRepository,
        scheduler: FakeReminderScheduler = FakeReminderScheduler(),
        id: String = "m1",
        nickname: String = "",
        dose: String = "",
        interval: Int? = null,
        times: List<ReminderTime> = emptyList(),
    ) = UpdateMedicationDetailsUseCase(repository, scheduler)(id, nickname, dose, interval, times)

    @Test
    fun `text is trimmed and everything is stored`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))

        val result = update(repository, nickname = "  Morning pill ", dose = " 2 pills ", interval = 8)

        assertEquals(AppResult.Success(Unit), result)
        val saved = repository.getMedication("m1")
        assertEquals("Morning pill", saved?.customUserNickname)
        assertEquals("2 pills", saved?.doseAmount)
        assertEquals(8, saved?.intervalHours)
    }

    @Test
    fun `blank nickname and dose are cleared`() = runTest {
        val repository = FakeMedicationRepository(
            listOf(medication.copy(customUserNickname = "Old", doseAmount = "1 pill", intervalHours = 6)),
        )

        update(repository, nickname = "   ", dose = "  ", interval = null)

        val saved = repository.getMedication("m1")
        assertNull(saved?.customUserNickname)
        assertNull(saved?.doseAmount)
        assertNull(saved?.intervalHours)
    }

    @Test
    fun `invalid input is rejected and nothing changes`() = runTest {
        suspend fun assertRejected(reason: ValidationReason, run: suspend (FakeMedicationRepository) -> AppResult<Unit>) {
            val repository = FakeMedicationRepository(listOf(medication))
            assertEquals(AppResult.Failure(DomainError.Validation(reason)), run(repository))
            assertEquals(medication, repository.getMedication("m1"))
        }

        assertRejected(ValidationReason.NICKNAME_TOO_LONG) { update(it, nickname = "x".repeat(41)) }
        assertRejected(ValidationReason.DOSE_TOO_LONG) { update(it, dose = "x".repeat(41)) }
        assertRejected(ValidationReason.INTERVAL_OUT_OF_RANGE) { update(it, interval = 0) }
        assertRejected(ValidationReason.INTERVAL_OUT_OF_RANGE) { update(it, interval = 169) }
        assertRejected(ValidationReason.TOO_MANY_REMINDERS) {
            update(it, times = (0 until 11).map { hour -> ReminderTime(hour, 0) })
        }
    }
    @Test
    fun `unknown medication is reported`() = runTest {
        val scheduler = FakeReminderScheduler()
        val result = update(FakeMedicationRepository(), scheduler, id = "nope", times = listOf(ReminderTime(8, 0)))

        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND)),
            result,
        )
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `reminder times are de-duplicated, sorted, stored and scheduled together`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))
        val scheduler = FakeReminderScheduler()
        val evening = ReminderTime(20, 0)
        val morning = ReminderTime(8, 30)

        update(repository, scheduler, times = listOf(evening, morning, evening))

        assertEquals(listOf(morning, evening), repository.getMedication("m1")?.reminderTimes)
        assertEquals(
            listOf(FakeReminderScheduler.Scheduled("m1", "Advil", listOf(morning, evening))),
            scheduler.scheduled,
        )
    }

    @Test
    fun `notifications use the nickname when set`() = runTest {
        val scheduler = FakeReminderScheduler()
        val time = ReminderTime(8, 0)

        update(FakeMedicationRepository(listOf(medication)), scheduler, nickname = "Morning pill", times = listOf(time))

        assertEquals(listOf(FakeReminderScheduler.Scheduled("m1", "Morning pill", listOf(time))), scheduler.scheduled)
    }

    @Test
    fun `removing every reminder cancels the scheduled work`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication.copy(reminderTimes = listOf(ReminderTime(9, 0)))))
        val scheduler = FakeReminderScheduler()

        update(repository, scheduler, times = emptyList())

        assertTrue(repository.getMedication("m1")?.reminderTimes.orEmpty().isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf("m1"), scheduler.cancelled)
    }
}