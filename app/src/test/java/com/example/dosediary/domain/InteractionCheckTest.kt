package com.example.dosediary.domain

import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.DrugInteractionRepository
import com.example.dosediary.domain.interaction.InteractionCheckResult
import com.example.dosediary.domain.interaction.InteractionMatcher
import com.example.dosediary.domain.interaction.InteractionSeverity
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.repository.MedicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInteractionRepository(var result: AppResult<String?>) : DrugInteractionRepository {
    var calls = 0
    override suspend fun getInteractionText(medication: Medication): AppResult<String?> {
        calls++
        return result
    }
}

class InteractionMatcherTest {

    private val warfarin = Medication(id = "w", commercialName = "Coumadin", activeSubstance = "Warfarin")
    private val combo = Medication(id = "c", commercialName = "Combo", activeSubstance = "Ibuprofen, Caffeine and Paracetamol")

    @Test
    fun `combined substances are split into individual terms`() {
        assertEquals(listOf("ibuprofen", "caffeine", "paracetamol"), InteractionMatcher.substancesOf(combo))
    }

    @Test
    fun `finds a substance case-insensitively and quotes its sentence`() {
        val text = "Anticoagulants may be affected. Concomitant use with WARFARIN increases bleeding risk. Take with food."
        val warning = InteractionMatcher.findWarnings(text, listOf(warfarin)).single()

        assertEquals("w", warning.savedMedicationId)
        assertEquals("warfarin", warning.matchedTerm)
        assertEquals("Concomitant use with WARFARIN increases bleeding risk.", warning.excerpt)
    }

    @Test
    fun `matches whole words only`() {
        val text = "Not related to warfarinlike compounds."
        assertTrue(InteractionMatcher.findWarnings(text, listOf(warfarin)).isEmpty())
    }

    @Test
    fun `uses nickname for display but the substance for matching`() {
        val nicknamed = warfarin.copy(customUserNickname = "Blood thinner")
        val warning = InteractionMatcher.findWarnings("Avoid warfarin.", listOf(nicknamed)).single()
        assertEquals("Blood thinner", warning.savedMedicationName)
    }

    @Test
    fun `at most one warning per saved medication`() {
        val warnings = InteractionMatcher.findWarnings("Caffeine and ibuprofen interact.", listOf(combo))
        assertEquals(1, warnings.size)
    }

    @Test
    fun `medications without an active substance never match`() {
        val unknown = Medication(id = "u", commercialName = "Mystery")
        assertTrue(InteractionMatcher.findWarnings("mystery mystery", listOf(unknown)).isEmpty())
    }

    @Test
    fun `severity is high for contraindication wording`() {
        assertEquals(InteractionSeverity.HIGH, InteractionMatcher.severityOf("Concomitant use with warfarin is contraindicated."))
        assertEquals(InteractionSeverity.HIGH, InteractionMatcher.severityOf("Do not use with warfarin."))
        assertEquals(InteractionSeverity.HIGH, InteractionMatcher.severityOf("Warfarin raises the risk of serious bleeding."))
    }

    @Test
    fun `severity is moderate for caution wording`() {
        assertEquals(InteractionSeverity.MODERATE, InteractionMatcher.severityOf("Warfarin may increase the effect of this drug."))
        assertEquals(InteractionSeverity.MODERATE, InteractionMatcher.severityOf("Monitor patients taking warfarin."))
    }

    @Test
    fun `severity is info for a bare mention`() {
        assertEquals(InteractionSeverity.INFO, InteractionMatcher.severityOf("Warfarin was included in the study."))
    }

    @Test
    fun `warnings are sorted by severity, most serious first`() {
        val aspirin = Medication(id = "a", commercialName = "Aspirin", activeSubstance = "Aspirin")
        val text = "Aspirin was included in the study. Warfarin is contraindicated."
        val warnings = InteractionMatcher.findWarnings(text, listOf(aspirin, warfarin))

        assertEquals(listOf("w", "a"), warnings.map { it.savedMedicationId })
        assertEquals(listOf(InteractionSeverity.HIGH, InteractionSeverity.INFO), warnings.map { it.severity })
    }

    @Test
    fun `blank label text produces no warnings`() {
        assertTrue(InteractionMatcher.findWarnings("   ", listOf(warfarin)).isEmpty())
    }

    @Test
    fun `very long sentences are shortened with an ellipsis`() {
        val text = "Warfarin " + "x".repeat(600) + "."
        val excerpt = InteractionMatcher.findWarnings(text, listOf(warfarin)).single().excerpt
        assertTrue(excerpt.length <= 241)
        assertTrue(excerpt.endsWith("\u2026"))
    }
}

class CheckMedicationInteractionsUseCaseTest {

    private val candidate = Medication(id = "new", commercialName = "Advil", activeSubstance = "Ibuprofen")
    private val warfarin = Medication(id = "w", commercialName = "Coumadin", activeSubstance = "Warfarin")

    @Test
    fun `warns when the new label mentions a saved medication`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success("Do not combine with warfarin."))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)))(candidate)

        val warnings = (result as InteractionCheckResult.Warnings).items
        assertEquals("w", warnings.single().savedMedicationId)
    }

    @Test
    fun `nothing saved means nothing to compare and no network call`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success("warfarin"))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository())(candidate)

        assertEquals(InteractionCheckResult.NothingFound, result)
        assertEquals(0, repo.calls)
    }

    @Test
    fun `the candidate itself is not compared against`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success("ibuprofen"))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(candidate)))(candidate)
        assertEquals(InteractionCheckResult.NothingFound, result)
    }

    @Test
    fun `label without matches reports nothing found`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success("Avoid alcohol."))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)))(candidate)
        assertEquals(InteractionCheckResult.NothingFound, result)
    }

    @Test
    fun `missing interaction section is unknown rather than clear`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success(null))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)))(candidate)
        assertEquals(InteractionCheckResult.Unknown(), result)
    }

    @Test
    fun `network failure fails open as unknown with the error`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Failure(DomainError.NoInternet))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)))(candidate)
        assertEquals(InteractionCheckResult.Unknown(DomainError.NoInternet), result)
    }

    @Test
    fun `highest severity of a result is exposed`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success("Warfarin is contraindicated."))
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(warfarin)))(candidate)
        assertEquals(InteractionSeverity.HIGH, (result as InteractionCheckResult.Warnings).highestSeverity)
    }

    @Test
    fun `saved medications without usable substances skip the network call`() = runTest {
        val repo = FakeInteractionRepository(AppResult.Success("mystery"))
        val saved = Medication(id = "u", commercialName = "Mystery")
        val result = CheckMedicationInteractionsUseCase(repo, FakeMedicationRepository(listOf(saved)))(candidate)

        assertEquals(InteractionCheckResult.NothingFound, result)
        assertEquals(0, repo.calls)
    }

    @Test
    fun `a hanging lookup times out as unknown instead of blocking`() = runTest {
        val hanging = object : DrugInteractionRepository {
            override suspend fun getInteractionText(medication: Medication): AppResult<String?> = awaitCancellation()
        }
        val result = CheckMedicationInteractionsUseCase(hanging, FakeMedicationRepository(listOf(warfarin)))(candidate)

        assertEquals(InteractionCheckResult.Unknown(DomainError.Timeout), result)
        assertEquals(CheckMedicationInteractionsUseCase.CHECK_TIMEOUT_MILLIS, currentTime)
    }

    @Test
    fun `a local storage failure fails open as unknown`() = runTest {
        val broken = object : MedicationRepository by FakeMedicationRepository() {
            override fun observeMedications() = flow<List<Medication>> { throw IllegalStateException("db closed") }
        }
        val repo = FakeInteractionRepository(AppResult.Success("warfarin"))

        val result = CheckMedicationInteractionsUseCase(repo, broken)(candidate)

        assertEquals(InteractionCheckResult.Unknown(DomainError.Storage), result)
        assertEquals(0, repo.calls)
    }

    @Test
    fun `cancellation is propagated, not reported as a result`() = runTest {
        val hanging = object : DrugInteractionRepository {
            override suspend fun getInteractionText(medication: Medication): AppResult<String?> = awaitCancellation()
        }
        val useCase = CheckMedicationInteractionsUseCase(hanging, FakeMedicationRepository(listOf(warfarin)))
        var completed = false
        val job = launch {
            try {
                useCase(candidate)
                completed = true
            } catch (e: CancellationException) {
                throw e
            }
        }
        advanceTimeBy(100)
        job.cancel()
        job.join()

        assertFalse(completed)
        assertTrue(job.isCancelled)
    }
}