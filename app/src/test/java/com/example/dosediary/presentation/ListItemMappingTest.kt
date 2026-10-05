package com.example.dosediary.presentation

import com.example.dosediary.R
import com.example.dosediary.domain.FakeMedicationRepository
import com.example.dosediary.domain.FakeReminderScheduler
import com.example.dosediary.domain.FakeSymptomRepository
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.DeleteSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ObserveSymptomsUseCase
import com.example.dosediary.presentation.common.UiTextFormatter
import com.example.dosediary.presentation.dashboard.DashboardUiState
import com.example.dosediary.presentation.dashboard.DashboardViewModel
import com.example.dosediary.presentation.dashboard.MedicationItemUi
import com.example.dosediary.presentation.symptom.SymptomDiaryUiState
import com.example.dosediary.presentation.symptom.SymptomDiaryViewModel
import com.example.dosediary.presentation.symptom.SymptomItemUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Deterministic formatter: lets the tests see exactly which strings the UI will receive. */
private class FakeUiTextFormatter(var locale: String = "en") : UiTextFormatter {
    override fun string(id: Int, vararg args: Any): String =
        if (args.isEmpty()) "s$id" else "s$id(${args.joinToString(",")})"

    override fun dateTime(epochMillis: Long): String = "dt$epochMillis"
    override fun time(time: ReminderTime): String = "%02d:%02d".format(time.hour, time.minute)
    override fun localeKey(): String = locale
}

class MedicationItemUiTest {

    private val text = FakeUiTextFormatter()
    private val advil = Medication(id = "a", commercialName = "Advil", activeSubstance = "Ibuprofen")

    @Test
    fun `plain medication gets the set-up-schedule prompt and no optional lines`() {
        val item = MedicationItemUi.from(advil, text)

        assertEquals("a", item.id)
        assertEquals("Advil", item.title)
        assertEquals("Advil", item.displayName)
        assertNull(item.nickname)
        assertNull(item.frequency)
        assertEquals("Ibuprofen", item.substance)
        assertEquals("s${R.string.action_set_reminder}", item.reminderText)
    }

    @Test
    fun `frequency line says every day or every N days once there are intakes`() {
        val intakes = listOf(Intake(ReminderTime(8, 0)))

        assertEquals(
            "s${R.string.frequency_every_day}",
            MedicationItemUi.from(advil.copy(intakes = intakes), text).frequency,
        )
        assertEquals(
            "s${R.string.frequency_every_n_days}(3)",
            MedicationItemUi.from(advil.copy(frequencyDays = 3, intakes = intakes), text).frequency,
        )
        // Without intakes there is nothing to describe, whatever the frequency is.
        assertNull(MedicationItemUi.from(advil.copy(frequencyDays = 3), text).frequency)
    }

    @Test
    fun `intakes are formatted with their dose and joined once, in the mapper`() {
        val item = MedicationItemUi.from(
            advil.copy(
                intakes = listOf(
                    Intake(ReminderTime(8, 0), doseAmount = "2 pills"),
                    Intake(ReminderTime(16, 30), doseAmount = null, notify = false),
                    Intake(ReminderTime(21, 0), doseAmount = "  "),
                ),
            ),
            text,
        )

        // A dose goes through the localised "time - dose" pattern; no dose is just the time.
        assertEquals("s${R.string.intake_summary}(08:00,2 pills), 16:30, 21:00", item.reminderText)
    }
    @Test
    fun `nickname is kept separately and drives displayName`() {
        val item = MedicationItemUi.from(advil.copy(customUserNickname = "Morning pill"), text)
        assertEquals("Advil", item.title)
        assertEquals("Morning pill", item.nickname)
        assertEquals("Morning pill", item.displayName)
        assertNull(MedicationItemUi.from(advil.copy(customUserNickname = "  "), text).nickname)
    }
}

class SymptomItemUiTest {

    private val text = FakeUiTextFormatter()
    private fun symptom(tags: Set<SymptomTag> = emptySet(), notes: String = "", medicationName: String? = null) =
        Symptom(id = 7, medicationName = medicationName, severity = 5, tags = tags, notes = notes, loggedAtMillis = 1234)

    @Test
    fun `date, severity and title arrive as final strings`() {
        val item = SymptomItemUi.from(symptom(), text)

        assertEquals(7L, item.id)
        assertEquals("dt1234", item.dateText)
        assertEquals("5", item.severityText)
        assertEquals("s${R.string.symptom_general}", item.title)
        assertFalse(item.isLinkedToMedication)
    }

    @Test
    fun `a linked symptom is titled Related to plus the medication's name`() {
        val item = SymptomItemUi.from(symptom(medicationName = "Morning pill"), text)

        assertEquals("s${R.string.symptom_related_to}(Morning pill)", item.title)
        assertTrue(item.isLinkedToMedication)
    }

    @Test
    fun `a blank medication name counts as not linked`() {
        val item = SymptomItemUi.from(symptom(medicationName = "  "), text)

        assertEquals("s${R.string.symptom_general}", item.title)
        assertFalse(item.isLinkedToMedication)
    }

    @Test
    fun `few tags are all listed with no hidden text`() {
        val item = SymptomItemUi.from(symptom(setOf(SymptomTag.HEADACHE, SymptomTag.NAUSEA)), text)

        assertEquals(2, item.tags.size)
        assertNull(item.hiddenTagsText)
    }

    @Test
    fun `tags beyond the limit are replaced by a plus-N count`() {
        val item = SymptomItemUi.from(symptom(SymptomTag.entries.toSet()), text)

        assertEquals(SymptomItemUi.MAX_VISIBLE_TAGS, item.tags.size)
        val hidden = SymptomTag.entries.size - SymptomItemUi.MAX_VISIBLE_TAGS
        assertEquals("s${R.string.symptom_tags_more}($hidden)", item.hiddenTagsText)
    }

    @Test
    fun `exactly the limit shows no indicator`() {
        val tags = SymptomTag.entries.take(SymptomItemUi.MAX_VISIBLE_TAGS).toSet()
        assertNull(SymptomItemUi.from(symptom(tags), text).hiddenTagsText)
    }

    @Test
    fun `blank notes become null`() {
        assertNull(SymptomItemUi.from(symptom(notes = "  "), text).notes)
        assertEquals("note", SymptomItemUi.from(symptom(notes = "note"), text).notes)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `dashboard emits ready-to-display items`() = runTest(dispatcher) {
        val repo = FakeMedicationRepository(listOf(Medication("a", "Advil", activeSubstance = "Ibuprofen")))
        val viewModel = DashboardViewModel(
            ObserveSavedMedicationsUseCase(repo),
            DeleteMedicationUseCase(repo, FakeReminderScheduler()),
            FakeUiTextFormatter(),
            mappingDispatcher = dispatcher,
        )

        val state = viewModel.uiState.first { it is DashboardUiState.Success } as DashboardUiState.Success

        assertEquals(listOf("a"), state.medications.map { it.id })
        assertEquals("s${R.string.action_set_reminder}", state.medications.single().reminderText)
    }

    @Test
    fun `dashboard rebuilds its strings when the language changes`() = runTest(dispatcher) {
        val repo = FakeMedicationRepository(listOf(Medication("a", "Advil", intakes = listOf(Intake(ReminderTime(8, 0))))))
        val formatter = FakeUiTextFormatter(locale = "en")
        val viewModel = DashboardViewModel(
            ObserveSavedMedicationsUseCase(repo),
            DeleteMedicationUseCase(repo, FakeReminderScheduler()),
            object : UiTextFormatter by formatter {
                override fun string(id: Int, vararg args: Any) = "${formatter.locale}:" + formatter.string(id, *args)
            },
            mappingDispatcher = dispatcher,
        )
        val collector = launch { viewModel.uiState.collect {} }

        val before = (viewModel.uiState.first { it is DashboardUiState.Success } as DashboardUiState.Success)
        assertEquals(true, before.medications.single().frequency!!.startsWith("en:"))

        formatter.locale = "pl"
        viewModel.onLocaleMaybeChanged()

        val after = viewModel.uiState.value as DashboardUiState.Success
        assertEquals(true, after.medications.single().frequency!!.startsWith("pl:"))
        collector.cancel()
    }

    @Test
    fun `symptom diary emits ready-to-display items`() = runTest(dispatcher) {
        val repo = FakeSymptomRepository()
        repo.saveSymptom(Symptom(severity = 8, tags = setOf(SymptomTag.FEVER), loggedAtMillis = 99))
        val viewModel = SymptomDiaryViewModel(
            ObserveSymptomsUseCase(repo),
            DeleteSymptomUseCase(repo),
            FakeUiTextFormatter(),
            mappingDispatcher = dispatcher,
        )

        val state = viewModel.uiState.first { it is SymptomDiaryUiState.Success } as SymptomDiaryUiState.Success

        val item = state.symptoms.single()
        assertEquals("dt99", item.dateText)
        assertEquals("8", item.severityText)
        assertEquals(1, item.tags.size)
    }
}
