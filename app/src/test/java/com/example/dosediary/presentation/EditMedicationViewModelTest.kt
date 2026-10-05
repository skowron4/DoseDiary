package com.example.dosediary.presentation

import com.example.dosediary.R
import com.example.dosediary.domain.FakeMedicationRepository
import com.example.dosediary.domain.FakeReminderPermissions
import com.example.dosediary.domain.FakeReminderScheduler
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.DrugInteractionRepository
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.usecase.GetMedicationUseCase
import com.example.dosediary.domain.usecase.UpdateMedicationDetailsUseCase
import com.example.dosediary.presentation.medication.EditMedicationViewModel
import com.example.dosediary.presentation.medication.FrequencyMode
import com.example.dosediary.presentation.medication.PermissionPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/**
 * The notification permission flow of the edit screen: permissions are only requested when the user
 * switches a notification on, never while adding, editing or saving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditMedicationViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val eight = ReminderTime(8, 0)
    private val noon = ReminderTime(12, 0)

    private class Setup(
        val viewModel: EditMedicationViewModel,
        val repository: FakeMedicationRepository,
        val scheduler: FakeReminderScheduler,
        val permissions: FakeReminderPermissions,
    )

    private fun setup(
        medication: Medication = Medication(id = "m1", commercialName = "Advil"),
        permissions: FakeReminderPermissions = FakeReminderPermissions(),
    ): Setup {
        val repository = FakeMedicationRepository(listOf(medication))
        val scheduler = FakeReminderScheduler()
        val interactions = object : DrugInteractionRepository {
            override suspend fun getInteractionText(medication: Medication): AppResult<String?> =
                AppResult.Success(null)
        }
        val viewModel = EditMedicationViewModel(
            medicationId = medication.id,
            getMedication = GetMedicationUseCase(repository),
            updateDetails = UpdateMedicationDetailsUseCase(repository, scheduler) { 0L },
            checkInteractions = CheckMedicationInteractionsUseCase(interactions, repository),
            permissions = permissions,
        )
        return Setup(viewModel, repository, scheduler, permissions)
    }

    private val Setup.state get() = viewModel.uiState.value
    private fun Setup.intakeId(time: ReminderTime) = state.intakes.single { it.time == time }.id

    // --- Loading and editing -----------------------------------------------------------------

    @Test
    fun `stored frequency and intakes are loaded into the form`() {
        val s = setup(
            Medication(
                id = "m1",
                commercialName = "Advil",
                frequencyDays = 3,
                frequencyStartEpochDay = 10,
                intakes = listOf(Intake(eight, "2 pills", true), Intake(noon, null, false)),
            ),
        )

        assertEquals(FrequencyMode.EVERY_N_DAYS, s.state.frequencyMode)
        assertEquals("3", s.state.frequencyDaysText)
        assertEquals(listOf(eight, noon), s.state.intakes.map { it.time })
        assertEquals(listOf("2 pills", ""), s.state.intakes.map { it.dose })
        assertEquals(listOf(true, false), s.state.intakes.map { it.notify })
    }

    @Test
    fun `an every-day medication opens in every-day mode`() {
        assertEquals(FrequencyMode.EVERY_DAY, setup().state.frequencyMode)
    }

    @Test
    fun `a duplicate intake time is rejected with a message`() {
        val s = setup()
        s.viewModel.onAddIntake(eight)

        s.viewModel.onAddIntake(eight)

        assertEquals(1, s.state.intakes.size)
        assertEquals(R.string.error_duplicate_intake_time, s.state.errorMessage?.res)
    }

    @Test
    fun `a new intake copies the previous dose and the list stays sorted`() {
        val s = setup()
        s.viewModel.onAddIntake(ReminderTime(20, 0))
        s.viewModel.onIntakeDoseChange(s.intakeId(ReminderTime(20, 0)), "1 pill")

        s.viewModel.onAddIntake(eight)

        assertEquals(listOf(eight, ReminderTime(20, 0)), s.state.intakes.map { it.time })
        // The 08:00 row was added last, after the 20:00 one, so it copies that dose.
        assertEquals("1 pill", s.state.intakes.first().dose)
    }

    @Test
    fun `retiming an intake onto another intake's time is rejected`() {
        val s = setup()
        s.viewModel.onAddIntake(eight)
        s.viewModel.onAddIntake(noon)

        s.viewModel.onIntakeTimeChange(s.intakeId(noon), eight)

        assertEquals(listOf(eight, noon), s.state.intakes.map { it.time })
        assertEquals(R.string.error_duplicate_intake_time, s.state.errorMessage?.res)
    }

    // --- Permission flow ---------------------------------------------------------------------

    @Test
    fun `adding, editing and saving without touching a switch never starts a permission flow`() = runTest {
        val s = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = true))

        s.viewModel.onAddIntake(eight)
        s.viewModel.onIntakeDoseChange(s.intakeId(eight), "2 pills")
        s.viewModel.save()

        assertNull(s.state.permissionPrompt)
        assertFalse(s.state.intakes.single().notify)
    }

    @Test
    fun `a new intake starts switched on only when notifications are already allowed`() {
        val allowed = setup(permissions = FakeReminderPermissions(canPost = true))
        allowed.viewModel.onAddIntake(eight)
        assertTrue(allowed.state.intakes.single().notify)
        assertNull(allowed.state.permissionPrompt)

        val blocked = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = true))
        blocked.viewModel.onAddIntake(eight)
        assertFalse(blocked.state.intakes.single().notify)
    }

    @Test
    fun `switching on when notifications are allowed needs no prompt`() {
        val s = setup(permissions = FakeReminderPermissions(canPost = true))
        s.viewModel.onAddIntake(eight)
        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), false)

        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), true)

        assertTrue(s.state.intakes.single().notify)
        assertNull(s.state.permissionPrompt)
    }

    @Test
    fun `switching on asks for the runtime permission first and enables the switch only when granted`() {
        val s = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = true))
        s.viewModel.onAddIntake(eight)
        val id = s.intakeId(eight)

        s.viewModel.onIntakeNotifyChange(id, true)

        assertEquals(PermissionPrompt.REQUEST_NOTIFICATIONS, s.state.permissionPrompt)
        assertFalse("not on until the system says yes", s.state.intakes.single().notify)

        s.permissions.canPost = true
        s.viewModel.onNotificationPermissionResult(granted = true)

        assertTrue(s.state.intakes.single().notify)
        assertNull(s.state.permissionPrompt)
    }

    @Test
    fun `a denied permission keeps the switch off and explains`() {
        val s = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = true))
        s.viewModel.onAddIntake(eight)
        val id = s.intakeId(eight)
        s.viewModel.onIntakeNotifyChange(id, true)

        s.viewModel.onNotificationPermissionResult(granted = false)

        assertFalse(s.state.intakes.single().notify)
        assertEquals(R.string.notifications_permission_denied, s.state.errorMessage?.res)
        assertNull(s.state.permissionPrompt)
    }

    @Test
    fun `after one refusal the next attempt guides to the settings instead of asking again`() {
        val s = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = true))
        s.viewModel.onAddIntake(eight)
        val id = s.intakeId(eight)
        s.viewModel.onIntakeNotifyChange(id, true)
        s.viewModel.onNotificationPermissionResult(granted = false)

        s.viewModel.onIntakeNotifyChange(id, true)

        assertEquals(PermissionPrompt.NOTIFICATIONS_BLOCKED, s.state.permissionPrompt)
        assertFalse(s.state.intakes.single().notify)
    }

    @Test
    fun `notifications blocked in the system settings guide to the settings right away`() {
        val s = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = false))
        s.viewModel.onAddIntake(eight)

        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), true)

        assertEquals(PermissionPrompt.NOTIFICATIONS_BLOCKED, s.state.permissionPrompt)
    }

    @Test
    fun `switching off never asks for anything`() {
        val s = setup(permissions = FakeReminderPermissions(canPost = false, canRequest = true))
        s.viewModel.onAddIntake(eight)

        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), false)

        assertNull(s.state.permissionPrompt)
    }

    @Test
    fun `exact alarms are offered once after a notification is switched on`() {
        val permissions = FakeReminderPermissions(canPost = true, exactNeedsAccess = true, canExact = false)
        val s = setup(permissions = permissions)
        s.viewModel.onAddIntake(eight)
        s.viewModel.onAddIntake(noon)
        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), false)
        s.viewModel.onIntakeNotifyChange(s.intakeId(noon), false)

        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), true)
        assertEquals(PermissionPrompt.EXPLAIN_EXACT_ALARMS, s.state.permissionPrompt)

        s.viewModel.onPermissionPromptHandled()
        s.viewModel.onIntakeNotifyChange(s.intakeId(noon), true)
        assertNull("only offered once per screen", s.state.permissionPrompt)
    }

    @Test
    fun `exact alarms are not offered when they are already allowed or need no access`() {
        val allowed = setup(permissions = FakeReminderPermissions(canPost = true, exactNeedsAccess = true, canExact = true))
        allowed.viewModel.onAddIntake(eight)
        allowed.viewModel.onIntakeNotifyChange(allowed.intakeId(eight), true)
        assertNull(allowed.state.permissionPrompt)

        val oldAndroid = setup(permissions = FakeReminderPermissions(canPost = true, exactNeedsAccess = false, canExact = true))
        oldAndroid.viewModel.onAddIntake(eight)
        oldAndroid.viewModel.onIntakeNotifyChange(oldAndroid.intakeId(eight), true)
        assertNull(oldAndroid.state.permissionPrompt)
    }

    @Test
    fun `exact alarms are offered after the runtime permission was granted too`() {
        val permissions = FakeReminderPermissions(canPost = false, canRequest = true, exactNeedsAccess = true, canExact = false)
        val s = setup(permissions = permissions)
        s.viewModel.onAddIntake(eight)
        s.viewModel.onIntakeNotifyChange(s.intakeId(eight), true)

        permissions.canPost = true
        s.viewModel.onNotificationPermissionResult(granted = true)

        assertEquals(PermissionPrompt.EXPLAIN_EXACT_ALARMS, s.state.permissionPrompt)
        assertTrue(s.state.intakes.single().notify)
    }

    // --- Saving ------------------------------------------------------------------------------

    @Test
    fun `save stores the frequency and the intakes and schedules them`() = runTest {
        val s = setup()
        s.viewModel.onNicknameChange("Morning pill")
        s.viewModel.onFrequencyModeChange(FrequencyMode.EVERY_N_DAYS)
        s.viewModel.onFrequencyDaysChange("4")
        s.viewModel.onAddIntake(eight)
        s.viewModel.onIntakeDoseChange(s.intakeId(eight), "2 pills")

        s.viewModel.save()
        s.viewModel.saved.first()

        val saved = s.repository.getMedication("m1")!!
        assertEquals("Morning pill", saved.customUserNickname)
        assertEquals(4, saved.frequencyDays)
        assertEquals(listOf(Intake(eight, "2 pills", notify = true)), saved.intakes)
        assertEquals(saved.intakes, s.scheduler.scheduled.single().intakes)
    }

    @Test
    fun `every-day mode ignores whatever is typed in the days field`() = runTest {
        val s = setup()
        s.viewModel.onFrequencyDaysChange("9")

        s.viewModel.save()
        s.viewModel.saved.first()

        assertEquals(1, s.repository.getMedication("m1")!!.frequencyDays)
    }

    @Test
    fun `an empty days field is reported instead of saved`() = runTest {
        val s = setup()
        s.viewModel.onFrequencyModeChange(FrequencyMode.EVERY_N_DAYS)
        s.viewModel.onFrequencyDaysChange("")

        s.viewModel.save()

        assertEquals(R.string.error_frequency_range, s.state.errorMessage?.res)
        assertEquals(1, s.repository.getMedication("m1")!!.frequencyDays)
        assertFalse(s.state.isSaving)
    }

    @Test
    fun `saving reports a silent reminder when a notification is on but cannot be shown`() = runTest {
        val s = setup(
            Medication(id = "m1", commercialName = "Advil", intakes = listOf(Intake(eight, notify = true))),
            FakeReminderPermissions(canPost = false),
        )

        s.viewModel.save()

        assertTrue(s.viewModel.saved.first())
    }

    @Test
    fun `saving is not silent when notifications work or no notification is on`() = runTest {
        val working = setup(
            Medication(id = "m1", commercialName = "Advil", intakes = listOf(Intake(eight, notify = true))),
            FakeReminderPermissions(canPost = true),
        )
        working.viewModel.save()
        assertFalse(working.viewModel.saved.first())

        val allOff = setup(
            Medication(id = "m1", commercialName = "Advil", intakes = listOf(Intake(eight, notify = false))),
            FakeReminderPermissions(canPost = false),
        )
        allOff.viewModel.save()
        assertFalse(allOff.viewModel.saved.first())
    }
}
