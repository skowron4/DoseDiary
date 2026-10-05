package com.example.dosediary.domain.interaction

import com.example.dosediary.domain.model.Medication

/**
 * Finds saved medications' active substances inside a label's free-text interaction section and
 * estimates how serious the mention is. Pure and deterministic so it can be unit tested without any
 * network.
 */
object InteractionMatcher {

    /** Shorter terms ("ion", "oil") match too much unrelated text. */
    const val MIN_TERM_LENGTH = 4
    private const val MAX_EXCERPT_LENGTH = 240
    private const val ELLIPSIS = "\u2026"

    private val HIGH_SEVERITY = Regex(
        "\\b(contraindicated|contraindication|do not (use|take|combine|administer)|should not be (used|taken|combined)|" +
            "avoid(ed|ance)?|not recommended|serious|severe|life[- ]threatening|fatal|death|" +
            "hemorrhage|haemorrhage|bleeding|toxicity|overdose)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val MODERATE_SEVERITY = Regex(
        "\\b(may (increase|decrease|reduce|enhance|potentiate|alter|affect)|increase[sd]?|decrease[sd]?|reduce[sd]?|" +
            "enhance[sd]?|monitor(ed|ing)?|caution|cautiously|adjust(ed|ment)?|risk|interact(s|ion)?|" +
            "concomitant|concurrent|co-?administ\\w*)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Splits e.g. "Ibuprofen, Caffeine and Paracetamol" into individual lower-case substances. */
    fun substancesOf(medication: Medication): List<String> =
        medication.activeSubstance.orEmpty()
            .lowercase()
            .split(Regex("[,;/+]|\\band\\b|\\bwith\\b"))
            .map { it.trim() }
            .filter { it.length >= MIN_TERM_LENGTH }
            .distinct()

    /**
     * At most one warning per saved medication (its first match). Most serious first; warnings of the
     * same severity keep the order of [saved].
     */
    fun findWarnings(interactionText: String, saved: List<Medication>): List<InteractionWarning> {
        val text = interactionText.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return emptyList()

        return saved.mapNotNull { medication ->
            substancesOf(medication).firstNotNullOfOrNull { term ->
                val match = Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE).find(text)
                match?.let {
                    val sentence = sentenceAround(text, it.range)
                    InteractionWarning(
                        savedMedicationId = medication.id,
                        savedMedicationName = medication.displayName,
                        matchedTerm = term,
                        excerpt = sentence.shortened(),
                        severity = severityOf(sentence),
                    )
                }
            }
        }.sortedByDescending { it.severity }
    }

    /** Estimates severity from the wording of one label [sentence]. */
    fun severityOf(sentence: String): InteractionSeverity = when {
        HIGH_SEVERITY.containsMatchIn(sentence) -> InteractionSeverity.HIGH
        MODERATE_SEVERITY.containsMatchIn(sentence) -> InteractionSeverity.MODERATE
        else -> InteractionSeverity.INFO
    }

    private fun sentenceAround(text: String, range: IntRange): String {
        val start = text.lastIndexOf(". ", range.first).let { if (it == -1) 0 else it + 2 }
        val end = text.indexOf(". ", range.last).let { if (it == -1) text.length else it + 1 }
        return text.substring(start, end).trim()
    }

    private fun String.shortened(): String =
        if (length <= MAX_EXCERPT_LENGTH) this else take(MAX_EXCERPT_LENGTH).trimEnd() + ELLIPSIS
}
