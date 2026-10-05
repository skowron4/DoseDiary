package com.example.dosediary.domain.interaction

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.first

/**
 * DRAFT (Phase 5): checks a medication the user is about to add against the ones already saved.
 *
 * Intended call site (not wired yet), in `SearchViewModel.save`:
 *
 * ```
 * fun save(medication: Medication) = viewModelScope.launch {
 *     when (val check = checkInteractions(medication)) {
 *         is Warnings -> _pendingInteraction.value = PendingInteraction(medication, check.items) // dialog
 *         else        -> persist(medication)            // NothingFound / Unknown: never block saving
 *     }
 * }
 * fun confirmSaveDespiteWarnings() = persist(pending.medication)   // "Save anyway"
 * fun dismissInteractionWarning()  { _pendingInteraction.value = null } // "Cancel"
 * ```
 */
class CheckMedicationInteractionsUseCase(
    private val interactions: DrugInteractionRepository,
    private val medications: MedicationRepository,
) {
    suspend operator fun invoke(candidate: Medication): InteractionCheckResult {
        // Only compare against OTHER saved medications (the candidate may already be saved).
        val saved = medications.observeMedications().first().filter { it.id != candidate.id }
        if (saved.isEmpty()) return InteractionCheckResult.NothingFound

        return when (val result = interactions.getInteractionText(candidate)) {
            is AppResult.Failure -> InteractionCheckResult.Unknown(result.error)
            is AppResult.Success -> {
                val text = result.data
                if (text.isNullOrBlank()) {
                    InteractionCheckResult.Unknown()
                } else {
                    InteractionMatcher.findWarnings(text, saved)
                        .takeIf { it.isNotEmpty() }
                        ?.let { InteractionCheckResult.Warnings(it) }
                        ?: InteractionCheckResult.NothingFound
                }
            }
        }
    }
}
