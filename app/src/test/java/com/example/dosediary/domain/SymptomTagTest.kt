package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomTagTest {

    @Test
    fun `encode and decode round-trip`() {
        val tags = setOf(SymptomTag.FEVER, SymptomTag.HEADACHE)
        assertEquals(tags, SymptomTag.decode(SymptomTag.encode(tags)))
    }

    @Test
    fun `encoding is stable regardless of selection order`() {
        assertEquals(
            SymptomTag.encode(linkedSetOf(SymptomTag.FEVER, SymptomTag.HEADACHE)),
            SymptomTag.encode(linkedSetOf(SymptomTag.HEADACHE, SymptomTag.FEVER)),
        )
        assertEquals("HEADACHE,FEVER", SymptomTag.encode(setOf(SymptomTag.FEVER, SymptomTag.HEADACHE)))
    }

    @Test
    fun `blank string decodes to no tags`() {
        assertTrue(SymptomTag.decode("").isEmpty())
        assertTrue(SymptomTag.decode("  ").isEmpty())
    }

    @Test
    fun `unknown keys are ignored`() {
        assertEquals(setOf(SymptomTag.NAUSEA), SymptomTag.decode("NAUSEA,FROM_THE_FUTURE"))
    }

    @Test
    fun `log symptom use case stores the selected tags`() = runTest {
        val repository = FakeSymptomRepository()
        val useCase = LogSymptomUseCase(repository, Clock { 1_000L })

        val result = useCase(
            medicationId = null,
            severity = 4,
            notes = "  after lunch ",
            tags = setOf(SymptomTag.NAUSEA, SymptomTag.FATIGUE),
        )

        assertEquals(AppResult.Success(Unit), result)
        val saved = repository.observeSymptoms().first().single()
        assertEquals(setOf(SymptomTag.NAUSEA, SymptomTag.FATIGUE), saved.tags)
        assertEquals("after lunch", saved.notes)
    }
}
