package com.example.dosediary.domain.usecase

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.MedicationDetails
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

class GetMedicationUseCase(private val repository: MedicationRepository) {
    suspend operator fun invoke(id: String): Medication? = repository.getMedication(id)
}

/**
 * Validates and stores the nickname, dosage and reminder times of a saved medication, then
 * (re)schedules its reminders so the notifications match what was saved.
 *
 * Blank text is stored as `null`; reminder times are de-duplicated and sorted.
 */
class UpdateMedicationDetailsUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(
        medicationId: String,
        nickname: String,
        doseAmount: String,
        intervalHours: Int?,
        reminderTimes: List<ReminderTime>,
    ): AppResult<Unit> {
        val cleanNickname = nickname.trim().takeIf { it.isNotEmpty() }
        val cleanDose = doseAmount.trim().takeIf { it.isNotEmpty() }
        val times = reminderTimes.distinct().sorted()

        val invalid = when {
            cleanNickname != null && cleanNickname.length > Medication.MAX_NICKNAME_LENGTH ->
                ValidationReason.NICKNAME_TOO_LONG
            cleanDose != null && cleanDose.length > Medication.MAX_DOSE_LENGTH ->
                ValidationReason.DOSE_TOO_LONG
            intervalHours != null &&
                intervalHours !in Medication.MIN_INTERVAL_HOURS..Medication.MAX_INTERVAL_HOURS ->
                ValidationReason.INTERVAL_OUT_OF_RANGE
            times.size > Medication.MAX_REMINDERS -> ValidationReason.TOO_MANY_REMINDERS
            else -> null
        }
        if (invalid != null) return AppResult.Failure(DomainError.Validation(invalid))

        val medication = repository.getMedication(medicationId)
            ?: return AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND))

        repository.updateDetails(
            medicationId,
            MedicationDetails(
                nickname = cleanNickname,
                doseAmount = cleanDose,
                intervalHours = intervalHours,
                reminderTimes = times,
            ),
        )

        if (times.isEmpty()) {
            scheduler.cancel(medicationId)
        } else {
            scheduler.schedule(medicationId, cleanNickname ?: medication.commercialName, times)
        }
        return AppResult.Success(Unit)
    }
}
