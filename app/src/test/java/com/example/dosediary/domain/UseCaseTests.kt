package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.usecase.CancelReminderUseCase
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.usecase.ScheduleReminderUseCase
import com.example.dosediary.domain.usecase.SearchMedicationUseCase
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMedicationUseCaseTest {

    @Test
    fun `query shorter than minimum is rejected without hitting the repository`() = runTest {
        val repository = FakeDrugSearchRepository()
        val result = SearchMedicationUseCase(repository)("a ")

        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.QUERY_TOO_SHORT)),
            result,
        )
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `query is trimmed and delegated`() = runTest {
        val medications = listOf(Medication(id = "1", commercialName = "Advil"))
        val repository = FakeDrugSearchRepository(AppResult.Success(medications))

        val result = SearchMedicationUseCase(repository)("  advil  ")

        assertEquals(AppResult.Success(medications), result)
        assertEquals(listOf("advil"), repository.queries)
    }

    @Test
    fun `repository failures are propagated`() = runTest {
        val repository = FakeDrugSearchRepository(AppResult.Failure(DomainError.NoInternet))
        assertEquals(AppResult.Failure(DomainError.NoInternet), SearchMedicationUseCase(repository)("advil"))
    }
}

class LogSymptomUseCaseTest {
    private val clock = Clock { 1_000L }

    @Test
    fun `severity outside 1 to 10 is rejected`() = runTest {
        val repository = FakeSymptomRepository()
        val useCase = LogSymptomUseCase(repository, clock)

        listOf(0, 11, -3).forEach { severity ->
            val result = useCase(medicationId = null, severity = severity, notes = "")
            assertEquals(
                AppResult.Failure(DomainError.Validation(ValidationReason.SEVERITY_OUT_OF_RANGE)),
                result,
            )
        }
        assertTrue(repository.observeSymptoms().first().isEmpty())
    }

    @Test
    fun `too long notes are rejected`() = runTest {
        val useCase = LogSymptomUseCase(FakeSymptomRepository(), clock)
        val result = useCase(medicationId = null, severity = 5, notes = "x".repeat(501))
        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.NOTES_TOO_LONG)),
            result,
        )
    }

    @Test
    fun `new symptom is stamped with the clock and notes are trimmed`() = runTest {
        val repository = FakeSymptomRepository()
        val result = LogSymptomUseCase(repository, clock)(medicationId = "med", severity = 7, notes = "  headache  ")

        assertEquals(AppResult.Success(Unit), result)
        val saved = repository.observeSymptoms().first().single()
        assertEquals(1_000L, saved.loggedAtMillis)
        assertEquals("headache", saved.notes)
        assertEquals("med", saved.medicationId)
        assertEquals(7, saved.severity)
    }

    @Test
    fun `editing keeps the original timestamp`() = runTest {
        val repository = FakeSymptomRepository()
        val useCase = LogSymptomUseCase(repository, clock)
        useCase(medicationId = null, severity = 3, notes = "a")
        val created = repository.observeSymptoms().first().single()

        useCase(
            id = created.id,
            medicationId = null,
            severity = 8,
            notes = "b",
            originalLoggedAtMillis = created.loggedAtMillis,
        )

        val updated = repository.observeSymptoms().first().single()
        assertEquals(created.id, updated.id)
        assertEquals(8, updated.severity)
        assertEquals(created.loggedAtMillis, updated.loggedAtMillis)
    }
}

class ReminderUseCasesTest {
    private val medication = Medication(id = "m1", commercialName = "Advil")

    @Test
    fun `scheduling persists the time and schedules the work`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))
        val scheduler = FakeReminderScheduler()
        val time = ReminderTime(8, 30)

        val result = ScheduleReminderUseCase(repository, scheduler)("m1", time)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(time, repository.getMedication("m1")?.reminderTime)
        assertEquals(listOf(FakeReminderScheduler.Scheduled("m1", "Advil", time)), scheduler.scheduled)
    }

    @Test
    fun `scheduling an unknown medication fails and schedules nothing`() = runTest {
        val scheduler = FakeReminderScheduler()
        val result = ScheduleReminderUseCase(FakeMedicationRepository(), scheduler)("nope", ReminderTime(8, 0))

        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND)),
            result,
        )
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `cancelling clears the stored time and cancels the work`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication.copy(reminderTime = ReminderTime(9, 0))))
        val scheduler = FakeReminderScheduler()

        CancelReminderUseCase(repository, scheduler)("m1")

        assertNull(repository.getMedication("m1")?.reminderTime)
        assertEquals(listOf("m1"), scheduler.cancelled)
    }

    @Test
    fun `deleting a medication also cancels its reminder`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))
        val scheduler = FakeReminderScheduler()

        DeleteMedicationUseCase(repository, scheduler)("m1")

        assertNull(repository.getMedication("m1"))
        assertEquals(listOf("m1"), scheduler.cancelled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reminder time rejects invalid values`() {
        ReminderTime(24, 0)
    }
}
