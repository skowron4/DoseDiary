package com.example.dosediary.domain.model

/** Pure rules for the "recent searches" list (most recent first). */
object SearchHistory {
    const val MAX_ENTRIES = 10

    /**
     * Returns [history] with [query] moved to (or inserted at) the top.
     *
     * Blank or too-short queries are ignored, entries are de-duplicated case-insensitively
     * (the newest spelling wins) and the list is capped at [MAX_ENTRIES].
     */
    fun withEntry(history: List<String>, query: String, minLength: Int): List<String> {
        val cleaned = query.trim().replace(WHITESPACE, " ")
        if (cleaned.length < minLength) return history
        return (listOf(cleaned) + history.filterNot { it.equals(cleaned, ignoreCase = true) })
            .take(MAX_ENTRIES)
    }

    fun withoutEntry(history: List<String>, query: String): List<String> =
        history.filterNot { it.equals(query, ignoreCase = true) }

    private val WHITESPACE = Regex("\\s+")
}
