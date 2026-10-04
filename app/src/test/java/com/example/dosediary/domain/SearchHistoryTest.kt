package com.example.dosediary.domain

import com.example.dosediary.domain.model.SearchHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {

    private val min = 2

    @Test
    fun `new query goes to the top`() {
        assertEquals(listOf("advil", "aspirin"), SearchHistory.withEntry(listOf("aspirin"), "advil", min))
    }

    @Test
    fun `duplicates are merged case-insensitively and the newest spelling wins`() {
        val result = SearchHistory.withEntry(listOf("aspirin", "Advil", "nurofen"), "advil", min)
        assertEquals(listOf("advil", "aspirin", "nurofen"), result)
    }

    @Test
    fun `query is trimmed and inner whitespace collapsed`() {
        assertEquals(listOf("adv il"), SearchHistory.withEntry(emptyList(), "  adv \t il  ", min))
    }

    @Test
    fun `blank or too short queries are ignored`() {
        val history = listOf("advil")
        assertEquals(history, SearchHistory.withEntry(history, "   ", min))
        assertEquals(history, SearchHistory.withEntry(history, "a", min))
    }

    @Test
    fun `history is capped and drops the oldest entries`() {
        val full = (1..SearchHistory.MAX_ENTRIES).map { "drug$it" }.reversed()
        val result = SearchHistory.withEntry(full, "newdrug", min)

        assertEquals(SearchHistory.MAX_ENTRIES, result.size)
        assertEquals("newdrug", result.first())
        assertEquals(false, "drug1" in result)
    }

    @Test
    fun `removing an entry ignores case`() {
        assertEquals(listOf("aspirin"), SearchHistory.withoutEntry(listOf("Advil", "aspirin"), "advil"))
    }
}
