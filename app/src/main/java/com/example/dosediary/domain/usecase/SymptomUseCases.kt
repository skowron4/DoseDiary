package com.example.dosediary.domain.usecase

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.repository.SymptomRepository
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.Flow

class ObserveSymptomsUseCase(private val repository: SymptomRepository) {
    operator fun invoke(): Flow<List<Symptom>> = repository.observeSymptoms()
}

class GetSymptomUseCase(private val repository: SymptomRepository) {
    suspend operator fun invoke(id: Long): Symptom? = repository.getSymptom(id)
}

/**
 * Creates or updates a symptom entry after validating it.
 *
 * - `id == 0` creates a new entry stamped with the current time.
 * - `id != 0` updates the existing entry, preserving the original timestamp.
 */
class LogSymptomUseCase(
    private val repository: SymptomRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        id: Long = 0,
        medicationId: String?,
        severity: Int,
        notes: String,
        tags: Set<SymptomTag> = emptySet(),
        originalLoggedAtMillis: Long? = null,
    ): AppResult<Unit> {
        if (severity !in Symptom.MIN_SEVERITY..Symptom.MAX_SEVERITY) {
            return AppResult.Failure(DomainError.Validation(ValidationReason.SEVERITY_OUT_OF_RANGE))
        }
        val cleanedNotes = notes.trim()
        if (cleanedNotes.length > Symptom.MAX_NOTES_LENGTH) {
            return AppResult.Failure(DomainError.Validation(ValidationReason.NOTES_TOO_LONG))
        }
        repository.saveSymptom(
            Symptom(
                id = id,
                medicationId = medicationId,
                severity = severity,
                tags = tags,
                notes = cleanedNotes,
                loggedAtMillis = originalLoggedAtMillis ?: clock.nowMillis(),
            ),
        )
        return AppResult.Success(Unit)
    }
}

class DeleteSymptomUseCase(private val repository: SymptomRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteSymptom(id)
}
