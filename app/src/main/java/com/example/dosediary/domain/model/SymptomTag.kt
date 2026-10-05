package com.example.dosediary.domain.model

/**
 * Common symptoms the user can tag an entry with in one tap.
 *
 * The enum [name] is the persisted key, so entries can be renamed/reordered in code and translated
 * in the UI without touching stored data. Never rename an existing constant.
 */
enum class SymptomTag {
    HEADACHE,
    NAUSEA,
    FATIGUE,
    FEVER,
    DIZZINESS,
    STOMACH_PAIN,
    COUGH,
    RASH,
    INSOMNIA,
    MUSCLE_PAIN,
    ;

    companion object {
        /** Serialises tags to a stable, comma-separated string (enum order, no duplicates). */
        fun encode(tags: Set<SymptomTag>): String =
            entries.filter { it in tags }.joinToString(",") { it.name }

        /** Parses [encode] output; unknown keys (e.g. written by a newer app version) are skipped. */
        fun decode(raw: String): Set<SymptomTag> {
            if (raw.isBlank()) return emptySet()
            val keys = raw.split(',').map { it.trim() }.toSet()
            return entries.filterTo(LinkedHashSet()) { it.name in keys }
        }
    }
}
