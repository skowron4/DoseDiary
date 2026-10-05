package com.example.dosediary.domain.interaction

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication

/*
 * Drug interaction checker.
 *
 * ARCHITECTURE
 * ------------
 * OpenFDA has no structured "drug A interacts with drug B" endpoint. What it does have is the
 * free-text `drug_interactions` section of each drug label (`/drug/label.json`). So the check is:
 *
 *   1. fetch the interaction text of the medication being viewed / added   (DrugInteractionRepository)
 *   2. look for the active substances of the user's other saved medications
 *      in it, and estimate a severity from the wording of the sentence      (InteractionMatcher)
 *   3. if found, warn, quoting the sentence from the label                  (InteractionCheckResult.Warnings)
 *
 * Where it plugs in: `SearchViewModel.save` (warn before a medication enters the diary, with
 * "Save anyway") and `EditMedicationViewModel` (banner for a medication that is already saved).
 *
 * Principles:
 *  - FAIL OPEN: no network / timeout / no label section => [InteractionCheckResult.Unknown], saving
 *    and editing keep working. A health app must not lose data because an API is down.
 *  - HONEST COPY: "label mentions X" is not "X is dangerous", and the severity is an estimate from
 *    the label's wording, not a clinical rating. Show the quoted sentence and tell the user to ask a
 *    pharmacist/doctor. Never display a green "safe" verdict, only "nothing found".
 *  - One direction: the viewed drug's label mentions the other saved drugs. The reverse (the saved
 *    drugs' labels mentioning the viewed drug) would need N requests; caching label text in Room is a
 *    possible follow-up.
 *  - Known limit: labels often name drug CLASSES ("anticoagulants", "NSAIDs") rather than
 *    substances, which substance matching cannot see.
 */

/**
 * Estimated seriousness of a label sentence. Ordered from least to most serious, so `maxOf` and
 * sorting work directly on the enum.
 */
enum class InteractionSeverity {
    /** The label merely mentions the other substance. */
    INFO,

    /** Wording such as "may increase", "monitor", "use caution", "dose adjustment". */
    MODERATE,

    /** Wording such as "avoid", "do not use", "contraindicated", "serious", "life-threatening". */
    HIGH,
}

/** One place where the viewed medication's label mentions something else the user already takes. */
data class InteractionWarning(
    val savedMedicationId: String,
    val savedMedicationName: String,
    /** The substance name found in the label text, e.g. "warfarin". */
    val matchedTerm: String,
    /** The label sentence containing the match, shortened for display. */
    val excerpt: String,
    val severity: InteractionSeverity = InteractionSeverity.INFO,
)

sealed interface InteractionCheckResult {
    /** The label was read and does not mention any other saved medication. This is NOT a safety guarantee. */
    data object NothingFound : InteractionCheckResult

    /** At least one mention, most serious first. */
    data class Warnings(val items: List<InteractionWarning>) : InteractionCheckResult {
        val highestSeverity: InteractionSeverity get() = items.maxOf { it.severity }
    }

    /**
     * The check could not be completed: [error] is set for network/server/timeout problems, `null`
     * when the label simply has no interaction section. Callers must still allow saving.
     */
    data class Unknown(val error: DomainError? = null) : InteractionCheckResult
}

/** Source of interaction information for a medication (OpenFDA label text in the real implementation). */
interface DrugInteractionRepository {
    /**
     * Returns the label's interaction text, `Success(null)` when the label has none (or does not
     * exist any more), or a failure. Medication ids are OpenFDA label ids.
     */
    suspend fun getInteractionText(medication: Medication): AppResult<String?>
}
