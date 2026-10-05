package com.example.dosediary.presentation

import com.example.dosediary.domain.FakeMedicationRepository
import com.example.dosediary.domain.FakeSymptomRepository
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.usecase.GetSymptomUseCase
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.util.Clock
import com.example.dosediary.presentation.symptom.AddSymptomViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Linking a symptom to one of the saved medications (or to none) when logging and editing it. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddSymptomViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val advil = Medication("advil", "Advil", customUserNickname = "Morning pill")
    private val aspirin = Medication("aspirin", "Aspirin")

    private val symptoms = FakeSymptomRepository()

    private fun viewModel(initialMedicationId: String? = null, symptomId: Long? = null) = AddSymptomViewModel(
        initialMedicationId = initialMedicationId,
        symptomId = symptomId,
        observeMedications = ObserveSavedMedicationsUseCase(FakeMedicationRepository(listOf(advil, aspirin))),
        getSymptom = GetSymptomUseCase(symptoms),
        logSymptom = LogSymptomUseCase(symptoms, Clock { 5_000L }),
    )

    @Test
    fun `the selector offers every saved medication and defaults to none`() {
        val state = viewModel().uiState.value

        assertEquals(listOf("advil", "aspirin"), state.medications.map { it.id })
        assertNull(state.selectedMedicationId)
    }

    @Test
    fun `opened from a medication card, that medication is preselected`() {
        assertEquals("aspirin", viewModel(initialMedicationId = "aspirin").uiState.value.selectedMedicationId)
    }

    @Test
    fun `a symptom saved without a selection is general`() = runTest {
        val viewModel = viewModel()

        viewModel.save()
        viewModel.saved.first()

        assertNull(symptoms.getSymptom(1)!!.medicationId)
    }

    @Test
    fun `the chosen medication is stored as the link`() = runTest {
        val viewModel = viewModel()
        viewModel.onMedicationSelected("advil")

        viewModel.save()
        viewModel.saved.first()

        assertEquals("advil", symptoms.getSymptom(1)!!.medicationId)
    }

    @Test
    fun `choosing none again removes the link`() = runTest {
        val viewModel = viewModel(initialMedicationId = "advil")
        viewModel.onMedicationSelected(null)

        viewModel.save()
        viewModel.saved.first()

        assertNull(symptoms.getSymptom(1)!!.medicationId)
    }

    @Test
    fun `editing loads the existing link and can change it, keeping the original time`() = runTest {
        symptoms.saveSymptom(Symptom(id = 0, medicationId = "advil", severity = 4, notes = "n", loggedAtMillis = 1_000L))
        val viewModel = viewModel(symptomId = 1)

        assertEquals("advil", viewModel.uiState.value.selectedMedicationId)

        viewModel.onMedicationSelected("aspirin")
        viewModel.save()
        viewModel.saved.first()

        val updated = symptoms.getSymptom(1)!!
        assertEquals("aspirin", updated.medicationId)
        assertEquals(1_000L, updated.loggedAtMillis)
    }
}
