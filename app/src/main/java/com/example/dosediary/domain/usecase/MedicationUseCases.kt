package com.example.dosediary.domain.usecase

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.repository.DrugSearchRepository
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.repository.ReminderScheduler
import kotlinx.coroutines.flow.Flow

/** Searches OpenFDA for medications by brand name. */
class SearchMedicationUseCase(private val repository: DrugSearchRepository) {

    suspend operator fun invoke(query: String): AppResult<List<Medication>> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return AppResult.Failure(DomainError.Validation(ValidationReason.QUERY_TOO_SHORT))
        }
        return repository.searchByBrandName(trimmed)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

class ObserveSavedMedicationsUseCase(private val repository: MedicationRepository) {
    operator fun invoke(): Flow<List<Medication>> = repository.observeMedications()
}

class SaveMedicationUseCase(private val repository: MedicationRepository) {
    suspend operator fun invoke(medication: Medication) = repository.saveMedication(medication)
}

/** Deletes a medication and makes sure its reminder no longer fires. */
class DeleteMedicationUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: String) {
        scheduler.cancel(id)
        repository.deleteMedication(id)
    }
}

/** Persists a daily reminder time for a saved medication and schedules the notification. */
class ScheduleReminderUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(medicationId: String, time: ReminderTime): AppResult<Unit> {
        val medication = repository.getMedication(medicationId)
            ?: return AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND))
        repository.updateReminder(medicationId, time)
        scheduler.schedule(medicationId, medication.displayName, time)
        return AppResult.Success(Unit)
    }
}

/**
 * Sets (or clears, when blank) the user's nickname for a medication. If a reminder is active it is
 * rescheduled so the notification text uses the new name.
 */
class UpdateMedicationNicknameUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(medicationId: String, nickname: String): AppResult<Unit> {
        val cleaned = nickname.trim().takeIf { it.isNotEmpty() }
        if (cleaned != null && cleaned.length > Medication.MAX_NICKNAME_LENGTH) {
            return AppResult.Failure(DomainError.Validation(ValidationReason.NICKNAME_TOO_LONG))
        }
        val medication = repository.getMedication(medicationId)
            ?: return AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND))

        repository.updateNickname(medicationId, cleaned)

        medication.reminderTime?.let { time ->
            scheduler.schedule(medicationId, (cleaned ?: medication.commercialName), time)
        }
        return AppResult.Success(Unit)
    }
}

/** Removes the reminder of a medication. */
class CancelReminderUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(medicationId: String) {
        scheduler.cancel(medicationId)
        repository.updateReminder(medicationId, null)
    }
}
