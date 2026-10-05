package com.example.dosediary.domain

import com.example.dosediary.domain.analytics.AnalyticsEvents
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.DrugInteractionRepository
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.usecase.SaveMedicationUseCase
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsLoggingTest {

    private val analytics = RecordingAnalyticsLogger()

    private val advil = Medication(
        id = "advil",
        commercialName = "Advil",
        activeSubstance = "Ibuprofen",
        intakes = listOf(Intake(ReminderTime(8, 0)), Intake(ReminderTime(20, 0))),
    )
    private val warfarin = Medication(id = "w", commercialName = "Coumadin", activeSubstance = "Warfarin")

    @Test
    fun `saving a new medication logs medication_added with the intake count only`() = runTest {
        SaveMedicationUseCase(FakeMedicationRepository(), analytics)(advil)

        val event = analytics.events.single()
        assertEquals(AnalyticsEvents.MEDICATION_ADDED, event.name)
        assertEquals(mapOf(AnalyticsEvents.PARAM_INTAKE_COUNT to "2"), event.params)
    }

    @Test
    fun `saving a medication that is already saved logs nothing`() = runTest {
        SaveMedicationUseCase(FakeMedicationRepository(listOf(advil)), analytics)(advil)

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `logging a symptom reports severity, link, tag count and whether it was an edit`() = runTest {
        val useCase = LogSymptomUseCase(FakeSymptomRepository(), Clock { 1_000L }, analytics)

        useCase(medicationId = "advil", severity = 6, notes = "private note", tags = setOf(SymptomTag.entries.first()))

        val event = analytics.events.single()
        assertEquals(AnalyticsEvents.SYMPTOM_LOGGED, event.name)
        assertEquals(
            mapOf(
                AnalyticsEvents.PARAM_SEVERITY to "6",
                AnalyticsEvents.PARAM_LINKED_TO_MEDICATION to "true",
                AnalyticsEvents.PARAM_TAG_COUNT to "1",
                AnalyticsEvents.PARAM_IS_UPDATE to "false",
            ),
            event.params,
        )
    }

    @Test
    fun `an invalid symptom is not logged`() = runTest {
        LogSymptomUseCase(FakeSymptomRepository(), Clock { 1_000L }, analytics)(
            medicationId = null,
            severity = 99,
            notes = "",
        )

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `a drug interaction warning logs interaction_warning_shown without drug names`() = runTest {
        val repo = object : DrugInteractionRepository {
            override suspend fun getInteractionText(medication: Medication) =
                AppResult.Success<String?>("Do not use with warfarin.")
        }

        CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)), analytics)(advil)

        val event = analytics.events.single()
        assertEquals(AnalyticsEvents.INTERACTION_WARNING_SHOWN, event.name)
        assertEquals(
            mapOf(
                AnalyticsEvents.PARAM_WARNING_COUNT to "1",
                AnalyticsEvents.PARAM_HIGHEST_SEVERITY to "HIGH",
            ),
            event.params,
        )
    }

    @Test
    fun `no warning found means no interaction event`() = runTest {
        val repo = object : DrugInteractionRepository {
            override suspend fun getInteractionText(medication: Medication) =
                AppResult.Success<String?>("Avoid alcohol.")
        }

        CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)), analytics)(advil)

        assertTrue(analytics.events.isEmpty())
    }
}
