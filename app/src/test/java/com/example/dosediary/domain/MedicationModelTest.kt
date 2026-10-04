package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.model.matchesQuery
import com.example.dosediary.domain.usecase.UpdateMedicationNicknameUseCase
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

class UpdateMedicationNicknameUseCaseTest {
    private val medication = Medication(id = "m1", commercialName = "Advil")

    @Test
    fun `nickname is trimmed and stored`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))

        val result = UpdateMedicationNicknameUseCase(repository, FakeReminderScheduler())("m1", "  Morning pill ")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("Morning pill", repository.getMedication("m1")?.customUserNickname)
    }

    @Test
    fun `blank nickname clears it`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication.copy(customUserNickname = "Old")))

        UpdateMedicationNicknameUseCase(repository, FakeReminderScheduler())("m1", "   ")

        assertNull(repository.getMedication("m1")?.customUserNickname)
    }

    @Test
    fun `too long nickname is rejected and nothing changes`() = runTest {
        val repository = FakeMedicationRepository(listOf(medication))

        val result = UpdateMedicationNicknameUseCase(repository, FakeReminderScheduler())("m1", "x".repeat(41))

        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.NICKNAME_TOO_LONG)),
            result,
        )
        assertNull(repository.getMedication("m1")?.customUserNickname)
    }

    @Test
    fun `unknown medication is reported`() = runTest {
        val result = UpdateMedicationNicknameUseCase(FakeMedicationRepository(), FakeReminderScheduler())("nope", "x")
        assertEquals(
            AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND)),
            result,
        )
    }

    @Test
    fun `an active reminder is rescheduled with the new name`() = runTest {
        val time = ReminderTime(8, 0)
        val repository = FakeMedicationRepository(listOf(medication.copy(reminderTime = time)))
        val scheduler = FakeReminderScheduler()

        UpdateMedicationNicknameUseCase(repository, scheduler)("m1", "Morning pill")

        assertEquals(listOf(FakeReminderScheduler.Scheduled("m1", "Morning pill", time)), scheduler.scheduled)
    }

    @Test
    fun `clearing the nickname reschedules with the commercial name`() = runTest {
        val time = ReminderTime(8, 0)
        val repository = FakeMedicationRepository(
            listOf(medication.copy(customUserNickname = "Old", reminderTime = time)),
        )
        val scheduler = FakeReminderScheduler()

        UpdateMedicationNicknameUseCase(repository, scheduler)("m1", "")

        assertEquals(listOf(FakeReminderScheduler.Scheduled("m1", "Advil", time)), scheduler.scheduled)
    }

    @Test
    fun `no reminder means nothing is scheduled`() = runTest {
        val scheduler = FakeReminderScheduler()
        UpdateMedicationNicknameUseCase(FakeMedicationRepository(listOf(medication)), scheduler)("m1", "x")
        assertTrue(scheduler.scheduled.isEmpty())
    }
}
