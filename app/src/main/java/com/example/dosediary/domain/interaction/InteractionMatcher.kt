package com.example.dosediary.domain.interaction

import com.example.dosediary.domain.model.Medication

/**
 * Finds saved medications' active substances inside a label's free-text interaction section.
 * Pure and deterministic so it can be unit tested without any network.
 */
object InteractionMatcher {

    /** Shorter terms ("ion", "oil") match too much unrelated text. */
    const val MIN_TERM_LENGTH = 4
    private const val MAX_EXCERPT_LENGTH = 240

    /** Splits e.g. "Ibuprofen, Caffeine and Paracetamol" into individual lower-case substances. */
    fun substancesOf(medication: Medication): List<String> =
        medication.activeSubstance.orEmpty()
            .lowercase()
            .split(Regex("[,;/+]|\\band\\b|\\bwith\\b"))
            .map { it.trim() }
            .filter { it.length >= MIN_TERM_LENGTH }
            .distinct()

    /** At most one warning per saved medication (its first match), in the order of [saved]. */
    fun findWarnings(interactionText: String, saved: List<Medication>): List<InteractionWarning> {
        val text = interactionText.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return emptyList()

        return saved.mapNotNull { medication ->
            substancesOf(medication).firstNotNullOfOrNull { term ->
                val match = Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE).find(text)
                match?.let {
                    InteractionWarning(
                        savedMedicationId = medication.id,
                        savedMedicationName = medication.displayName,
                        matchedTerm = term,
                        excerpt = sentenceAround(text, it.range),
                    )
                }
            }
        }
    }

    private fun sentenceAround(text: String, range: IntRange): String {
        val start = text.lastIndexOf(". ", range.first).let { if (it == -1) 0 else it + 2 }
        val end = text.indexOf(". ", range.last).let { if (it == -1) text.length else it + 1 }
        val sentence = text.substring(start, end).trim()
        return if (sentence.length <= MAX_EXCERPT_LENGTH) sentence else sentence.take(MAX_EXCERPT_LENGTH).trimEnd() + "…"
    }
}
