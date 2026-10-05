package com.example.dosediary.domain.interaction

import com.example.dosediary.domain.analytics.AnalyticsEvents
import com.example.dosediary.domain.analytics.AnalyticsLogger
import com.example.dosediary.domain.analytics.NoOpAnalyticsLogger
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.repository.MedicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks a medication (about to be added, or already saved) against the user's *other* saved ones.
 *
 * This never throws and never blocks for long: every failure (offline, server error, timeout, local
 * storage) comes back as [InteractionCheckResult.Unknown], so callers can always carry on saving or
 * editing. Coroutine cancellation is respected.
 */
class CheckMedicationInteractionsUseCase(
    private val interactions: DrugInteractionRepository,
    private val medications: MedicationRepository,
    private val analytics: AnalyticsLogger = NoOpAnalyticsLogger,
) {
    suspend operator fun invoke(candidate: Medication): InteractionCheckResult {
        val saved = try {
            // Only compare against OTHER saved medications (the candidate may already be saved).
            medications.observeMedications().first().filter { it.id != candidate.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return InteractionCheckResult.Unknown(DomainError.Storage)
        }
        if (saved.none { InteractionMatcher.substancesOf(it).isNotEmpty() }) return InteractionCheckResult.NothingFound

        // The HTTP client has its own, longer timeouts; this keeps "Save" from feeling stuck.
        val result = withTimeoutOrNull(CHECK_TIMEOUT_MILLIS) { interactions.getInteractionText(candidate) }
            ?: return InteractionCheckResult.Unknown(DomainError.Timeout)

        return when (result) {
            is AppResult.Failure -> InteractionCheckResult.Unknown(result.error)
            is AppResult.Success -> {
                val text = result.data
                if (text.isNullOrBlank()) {
                    InteractionCheckResult.Unknown()
                } else {
                    InteractionMatcher.findWarnings(text, saved)
                        .takeIf { it.isNotEmpty() }
                        ?.let { InteractionCheckResult.Warnings(it).also(::logWarning) }
                        ?: InteractionCheckResult.NothingFound
                }
            }
        }
    }

    private fun logWarning(warnings: InteractionCheckResult.Warnings) {
        analytics.logEvent(
            AnalyticsEvents.INTERACTION_WARNING_SHOWN,
            mapOf(
                AnalyticsEvents.PARAM_WARNING_COUNT to warnings.items.size.toString(),
                AnalyticsEvents.PARAM_HIGHEST_SEVERITY to warnings.highestSeverity.name,
            ),
        )
    }

    companion object {
        const val CHECK_TIMEOUT_MILLIS = 8_000L
    }
}
