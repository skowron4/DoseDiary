package com.example.dosediary.presentation

import com.example.dosediary.R
import com.example.dosediary.domain.FakeDrugSearchRepository
import com.example.dosediary.domain.FakeMedicationRepository
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.DrugInteractionRepository
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.repository.SearchHistoryRepository
import com.example.dosediary.domain.usecase.AddSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ClearSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ObserveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.RemoveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.SaveMedicationUseCase
import com.example.dosediary.domain.usecase.SearchMedicationUseCase
import com.example.dosediary.presentation.interaction.InteractionUiState
import com.example.dosediary.presentation.interaction.toUiState
import com.example.dosediary.presentation.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.dosediary.domain.interaction.InteractionCheckResult
import com.example.dosediary.domain.interaction.InteractionWarning

private class InMemoryHistory : SearchHistoryRepository {
    private val items = MutableStateFlow<List<String>>(emptyList())
    override fun observeHistory(): Flow<List<String>> = items
    override suspend fun addQuery(query: String) {
        items.value = listOf(query) + items.value.filterNot { it == query }
    }

    override suspend fun removeQuery(query: String) {
        items.value = items.value - query
    }

    override suspend fun clear() {
        items.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSaveInteractionTest {

    private val dispatcher = StandardTestDispatcher()
    private val warfarin = Medication(id = "w", commercialName = "Coumadin", activeSubstance = "Warfarin")
    private val advil = Medication(id = "advil", commercialName = "Advil", activeSubstance = "Ibuprofen")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Setup(
        val viewModel: SearchViewModel,
        val medications: FakeMedicationRepository,
        val interactions: ScriptedInteractions,
    )

    private class ScriptedInteractions(var result: AppResult<String?>) : DrugInteractionRepository {
        override suspend fun getInteractionText(medication: Medication) = result
    }

    private fun setup(label: AppResult<String?>, saved: List<Medication> = listOf(warfarin)): Setup {
        val medications = FakeMedicationRepository(saved)
        val interactions = ScriptedInteractions(label)
        val history = InMemoryHistory()
        val viewModel = SearchViewModel(
            searchMedication = SearchMedicationUseCase(FakeDrugSearchRepository()),
            observeSavedMedications = ObserveSavedMedicationsUseCase(medications),
            saveMedication = SaveMedicationUseCase(medications),
            observeHistory = ObserveSearchHistoryUseCase(history),
            addToHistory = AddSearchHistoryUseCase(history),
            removeFromHistory = RemoveSearchHistoryUseCase(history),
            clearHistory = ClearSearchHistoryUseCase(history),
            checkInteractions = CheckMedicationInteractionsUseCase(interactions, medications),
        )
        return Setup(viewModel, medications, interactions)
    }

    private suspend fun FakeMedicationRepository.ids() = observeMedications().first().map { it.id }.toSet()

    @Test
    fun `warnings hold the medication back, Save anyway stores it`() = runTest(dispatcher) {
        val s = setup(AppResult.Success("Do not combine with warfarin."))
        val collector = launch { s.viewModel.uiState.collect {} }

        s.viewModel.save(advil)
        advanceUntilIdle()

        val pending = s.viewModel.uiState.value.pendingInteraction
        assertNotNull(pending)
        assertEquals("advil", pending!!.medication.id)
        assertEquals("w", pending.warnings.single().savedMedicationId)
        assertEquals(setOf("w"), s.medications.ids())

        s.viewModel.confirmSaveDespiteInteractions()
        advanceUntilIdle()

        assertNull(s.viewModel.uiState.value.pendingInteraction)
        assertEquals(setOf("w", "advil"), s.medications.ids())
        assertEquals(R.string.medication_saved, s.viewModel.messages.first().res)
        collector.cancel()
    }

    @Test
    fun `cancelling the warning stores nothing`() = runTest(dispatcher) {
        val s = setup(AppResult.Success("Do not combine with warfarin."))
        val collector = launch { s.viewModel.uiState.collect {} }

        s.viewModel.save(advil)
        advanceUntilIdle()
        s.viewModel.dismissInteractionWarning()
        advanceUntilIdle()

        assertNull(s.viewModel.uiState.value.pendingInteraction)
        assertEquals(setOf("w"), s.medications.ids())
        collector.cancel()
    }

    @Test
    fun `no interactions found saves straight away`() = runTest(dispatcher) {
        val s = setup(AppResult.Success("Avoid alcohol."))
        val collector = launch { s.viewModel.uiState.collect {} }

        s.viewModel.save(advil)
        advanceUntilIdle()

        assertNull(s.viewModel.uiState.value.pendingInteraction)
        assertEquals(setOf("w", "advil"), s.medications.ids())
        assertEquals(R.string.medication_saved, s.viewModel.messages.first().res)
        collector.cancel()
    }

    @Test
    fun `offline still saves and says the check did not happen`() = runTest(dispatcher) {
        val s = setup(AppResult.Failure(DomainError.NoInternet))
        val collector = launch { s.viewModel.uiState.collect {} }

        s.viewModel.save(advil)
        advanceUntilIdle()

        assertNull(s.viewModel.uiState.value.pendingInteraction)
        assertEquals(setOf("w", "advil"), s.medications.ids())
        assertEquals(R.string.medication_saved_unchecked, s.viewModel.messages.first().res)
        collector.cancel()
    }

    @Test
    fun `a label without interaction text saves with the normal message`() = runTest(dispatcher) {
        val s = setup(AppResult.Success(null))
        val collector = launch { s.viewModel.uiState.collect {} }

        s.viewModel.save(advil)
        advanceUntilIdle()

        assertEquals(setOf("w", "advil"), s.medications.ids())
        assertEquals(R.string.medication_saved, s.viewModel.messages.first().res)
        collector.cancel()
    }

    @Test
    fun `first medication needs no network check at all`() = runTest(dispatcher) {
        val s = setup(AppResult.Failure(DomainError.NoInternet), saved = emptyList())
        val collector = launch { s.viewModel.uiState.collect {} }

        s.viewModel.save(advil)
        advanceUntilIdle()

        assertEquals(setOf("advil"), s.medications.ids())
        assertEquals(R.string.medication_saved, s.viewModel.messages.first().res)
        collector.cancel()
    }

    @Test
    fun `check results map to banner states`() {
        val warning = InteractionWarning("w", "Coumadin", "warfarin", "Warfarin.")

        assertEquals(InteractionUiState.NothingFound, InteractionCheckResult.NothingFound.toUiState())
        assertEquals(
            InteractionUiState.Unavailable(DomainError.Timeout),
            InteractionCheckResult.Unknown(DomainError.Timeout).toUiState(),
        )
        assertEquals(InteractionUiState.Unavailable(null), InteractionCheckResult.Unknown().toUiState())
        assertTrue(InteractionCheckResult.Warnings(listOf(warning)).toUiState() is InteractionUiState.Warnings)
    }
}
