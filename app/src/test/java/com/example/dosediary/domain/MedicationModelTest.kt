package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.model.matchesQuery
import com.example.dosediary.domain.usecase.UpdateMedicationDetailsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationModelTest {

    private val advil = Medication(
        id = "1",
        commercialName = "Advil",
        activeSubstance = "Ibuprofen",
        manufacturer = "Haleon",
    )

    @Test
    fun `display name is the commercial name when there is no nickname`() {
        assertEquals("Advil", advil.displayName)
        assertEquals("Ibuprofen", advil.displaySubtitle)
    }

    @Test
    fun `display name prefers the nickname and keeps the active substance as subtitle`() {
        val nicknamed = advil.copy(customUserNickname = "Morning pill")
        assertEquals("Morning pill", nicknamed.displayName)
        assertEquals("Ibuprofen", nicknamed.displaySubtitle)
    }

    @Test
    fun `blank nickname and blank substance are ignored`() {
        val blank = advil.copy(customUserNickname = "   ", activeSubstance = " ")
        assertEquals("Advil", blank.displayName)
        assertNull(blank.displaySubtitle)
    }

    @Test
    fun `query matches nickname, commercial name, substance and manufacturer ignoring case`() {
        val nicknamed = advil.copy(customUserNickname = "Morning pill")
        assertTrue(nicknamed.matchesQuery("morning"))
        assertTrue(nicknamed.matchesQuery("ADV"))
        assertTrue(nicknamed.matchesQuery("ibupro"))
        assertTrue(nicknamed.matchesQuery("hale"))
        assertFalse(nicknamed.matchesQuery("aspirin"))
    }

    @Test
    fun `blank query matches everything`() {
        assertTrue(advil.matchesQuery(""))
        assertTrue(advil.matchesQuery("   "))
    }
}
