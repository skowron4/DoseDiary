package com.example.dosediary.domain.interaction

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication

/*
 * DRAFT (Phase 5): drug interaction checker.
 *
 * ARCHITECTURE
 * ------------
 * OpenFDA has no structured "drug A interacts with drug B" endpoint. What it does have is the
 * free-text `drug_interactions` section of each drug label (`/drug/label.json`). So the check is:
 *
 *   1. fetch the interaction text of the medication being added           (DrugInteractionRepository)
 *   2. look for the active substances of the user's saved medications in it (InteractionMatcher)
 *   3. if found, warn BEFORE saving, quoting the sentence from the label    (InteractionCheckResult.Warnings)
 *
 * Where it plugs in: `SearchViewModel.save(medication)`, the single place where a medication
 * enters the diary. It would call CheckMedicationInteractionsUseCase first and only call
 * SaveMedicationUseCase when there is nothing to warn about or the user confirms. SaveMedicationUseCase
 * and MedicationRepository stay untouched, so saving remains a dumb, always-works operation.
 *
 * Principles:
 *  - FAIL OPEN: no network / no label section => [InteractionCheckResult.Unknown], saving proceeds
 *    (with a gentle "couldn't check" note). A health app must not lose data because an API is down.
 *  - HONEST COPY: "label mentions X" is not "X is dangerous". Show the quoted sentence and tell the
 *    user to ask a pharmacist/doctor. Never display a green "safe" verdict, only "nothing found".
 *  - v1 checks one direction (new drug's label mentions saved drugs). v2 can also scan the saved
 *    drugs' labels for the new drug (cache label text in Room to avoid N requests per save).
 *  - Known limit: labels often name drug CLASSES ("anticoagulants", "NSAIDs") rather than
 *    substances, which substance matching cannot see. A small class map (ibuprofen -> NSAIDs) is a
 *    possible v3.
 */

/** One place where the new medication's label mentions something the user already takes. */
data class InteractionWarning(
    val savedMedicationId: String,
    val savedMedicationName: String,
    /** The substance name found in the label text, e.g. "warfarin". */
    val matchedTerm: String,
    /** The label sentence containing the match, shortened for display. */
    val excerpt: String,
)

sealed interface InteractionCheckResult {
    /** The label was read and does not mention any saved medication. This is NOT a safety guarantee. */
    data object NothingFound : InteractionCheckResult

    data class Warnings(val items: List<InteractionWarning>) : InteractionCheckResult

    /**
     * The check could not be completed: [error] is set for network/server problems, `null` when the
     * label simply has no interaction section. Callers must still allow saving.
     */
    data class Unknown(val error: DomainError? = null) : InteractionCheckResult
}

/** Source of interaction information for a medication (OpenFDA label text in the real implementation). */
interface DrugInteractionRepository {
    /**
     * Returns the label's interaction text, `Success(null)` when the label has none, or a failure.
     *
     * Proposed implementation: `OpenFdaApi.getLabelById(id)` (search=`id:"…"`, limit=1) with
     * `DrugLabelDto.drugInteractions` mapped from `drug_interactions` (joined, whitespace-normalised),
     * run through the existing `safeNetworkCall`. Medication ids ARE OpenFDA label ids, so no lookup
     * by name is needed.
     */
    suspend fun getInteractionText(medication: Medication): AppResult<String?>
}
