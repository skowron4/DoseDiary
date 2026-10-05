package com.example.dosediary.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.InteractionCheckResult
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.repository.ReminderPermissions
import com.example.dosediary.domain.usecase.GetMedicationUseCase
import com.example.dosediary.domain.usecase.UpdateMedicationDetailsUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.runCatchingCancellable
import com.example.dosediary.presentation.common.toUiMessage
import com.example.dosediary.presentation.interaction.InteractionUiState
import com.example.dosediary.presentation.interaction.toUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable intake row. [id] is only a stable key for the list; it is not persisted. */
data class IntakeDraft(
    val id: Int,
    val time: ReminderTime,
    val dose: String,
    val notify: Boolean,
)

enum class FrequencyMode { EVERY_DAY, EVERY_N_DAYS }

/** What the screen has to do or show next in the permission flow of a notification toggle. */
enum class PermissionPrompt {
    /** Show the system POST_NOTIFICATIONS dialog (Android 13+). */
    REQUEST_NOTIFICATIONS,

    /** Notifications are blocked for good; explain and offer the app's notification settings. */
    NOTIFICATIONS_BLOCKED,

    /** Explain why exact alarms help and offer the special-access settings page. */
    EXPLAIN_EXACT_ALARMS,
}

data class EditMedicationUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val commercialName: String = "",
    val activeSubstance: String? = null,
    val nickname: String = "",
    val frequencyMode: FrequencyMode = FrequencyMode.EVERY_DAY,
    /** Raw text of the "every X days" field; parsed on save so the user can type freely. */
    val frequencyDaysText: String = "",
    val intakes: List<IntakeDraft> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: UiMessage? = null,
    val permissionPrompt: PermissionPrompt? = null,
    /** Possible interactions with the user's other saved medications (fail-open, never blocks editing). */
    val interactions: InteractionUiState = InteractionUiState.Idle,
)

/**
 * Edits the nickname, frequency and intakes (time + dose + notification) of one saved medication.
 *
 * Permissions are only asked for when the user switches a notification on, see [onIntakeNotifyChange].
 */
class EditMedicationViewModel(
    private val medicationId: String,
    private val getMedication: GetMedicationUseCase,
    private val updateDetails: UpdateMedicationDetailsUseCase,
    private val checkInteractions: CheckMedicationInteractionsUseCase,
    private val permissions: ReminderPermissions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditMedicationUiState())
    val uiState: StateFlow<EditMedicationUiState> = _uiState.asStateFlow()

    private val _saved = Channel<Boolean>(Channel.CONFLATED)

    /**
     * Emits once after the changes were stored; `true` when at least one notification is on but the
     * system will not show it (so the user is told they will not be alerted).
     */
    val saved: Flow<Boolean> = _saved.receiveAsFlow()

    private var nextDraftId = 0
    private var pendingNotifyIntakeId: Int? = null
    private var notificationDenials = 0
    private var exactAlarmsExplained = false
    private var loadedMedication: Medication? = null
    private var interactionJob: Job? = null

    init {
        viewModelScope.launch {
            val medication = runCatchingCancellable { getMedication(medicationId) }.getOrNull()
            if (medication == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        commercialName = medication.commercialName,
                        activeSubstance = medication.activeSubstance,
                        nickname = medication.customUserNickname.orEmpty(),
                        frequencyMode = if (medication.frequencyDays > 1) {
                            FrequencyMode.EVERY_N_DAYS
                        } else {
                            FrequencyMode.EVERY_DAY
                        },
                        frequencyDaysText = medication.frequencyDays.takeIf { d -> d > 1 }?.toString()
                            ?: DEFAULT_N_DAYS.toString(),
                        intakes = medication.intakes.map { intake -> intake.toDraft() },
                    )
                }
                loadedMedication = medication
                runInteractionCheck(medication)
            }
        }
    }

    /** Re-runs the interaction check, e.g. after the device came back online. */
    fun retryInteractionCheck() {
        loadedMedication?.let(::runInteractionCheck)
    }

    private fun runInteractionCheck(medication: Medication) {
        interactionJob?.cancel()
        _uiState.update { it.copy(interactions = InteractionUiState.Checking) }
        interactionJob = viewModelScope.launch {
            val result = runCatchingCancellable { checkInteractions(medication) }
                .getOrElse { InteractionCheckResult.Unknown(DomainError.Unknown) }
            _uiState.update { it.copy(interactions = result.toUiState()) }
        }
    }

    fun onNicknameChange(value: String) = _uiState.update {
        it.copy(nickname = value.take(Medication.MAX_NICKNAME_LENGTH), errorMessage = null)
    }

    // --- Frequency ---------------------------------------------------------------------------

    fun onFrequencyModeChange(mode: FrequencyMode) = _uiState.update {
        it.copy(frequencyMode = mode, errorMessage = null)
    }

    fun onFrequencyDaysChange(value: String) = _uiState.update {
        // Digits only; at most the width of MAX_FREQUENCY_DAYS.
        it.copy(frequencyDaysText = value.filter(Char::isDigit).take(MAX_FREQUENCY_DIGITS), errorMessage = null)
    }

    // --- Intakes -----------------------------------------------------------------------------

    /**
     * Adds an intake. It starts with the previous intake's dose, and with its notification on only
     * when notifications are already allowed, so that adding a row never triggers a permission prompt.
     */
    fun onAddIntake(time: ReminderTime) = _uiState.update { state ->
        when {
            state.intakes.size >= Medication.MAX_INTAKES -> state.withError(ValidationReason.TOO_MANY_INTAKES)
            state.intakes.any { it.time == time } -> state.withError(ValidationReason.DUPLICATE_INTAKE_TIME)
            else -> {
                val draft = IntakeDraft(
                    id = nextDraftId++,
                    time = time,
                    dose = state.intakes.lastOrNull()?.dose.orEmpty(),
                    notify = permissions.canPostNotifications(),
                )
                state.copy(intakes = (state.intakes + draft).sortedBy { it.time }, errorMessage = null)
            }
        }
    }

    fun onIntakeTimeChange(id: Int, time: ReminderTime) = _uiState.update { state ->
        if (state.intakes.any { it.id != id && it.time == time }) {
            state.withError(ValidationReason.DUPLICATE_INTAKE_TIME)
        } else {
            state.copy(
                intakes = state.intakes.map { if (it.id == id) it.copy(time = time) else it }.sortedBy { it.time },
                errorMessage = null,
            )
        }
    }

    fun onIntakeDoseChange(id: Int, value: String) = updateIntake(id) {
        it.copy(dose = value.take(Medication.MAX_DOSE_LENGTH))
    }

    fun onRemoveIntake(id: Int) = _uiState.update { state ->
        state.copy(intakes = state.intakes.filterNot { it.id == id }, errorMessage = null)
    }

    /**
     * Switching a notification off is always free. Switching it on first makes sure the app is allowed
     * to show notifications ([PermissionPrompt.REQUEST_NOTIFICATIONS] / [PermissionPrompt.NOTIFICATIONS_BLOCKED]),
     * and afterwards offers exact alarms once ([PermissionPrompt.EXPLAIN_EXACT_ALARMS]).
     */
    fun onIntakeNotifyChange(id: Int, enabled: Boolean) {
        if (!enabled) {
            updateIntake(id) { it.copy(notify = false) }
            return
        }
        when {
            permissions.canPostNotifications() -> {
                updateIntake(id) { it.copy(notify = true) }
                explainExactAlarmsIfUseful()
            }
            // After one refusal the system dialog tends to be skipped, so guide to settings right away.
            notificationDenials > 0 || !permissions.canRequestNotificationPermission() -> {
                _uiState.update { it.copy(permissionPrompt = PermissionPrompt.NOTIFICATIONS_BLOCKED) }
            }
            else -> {
                pendingNotifyIntakeId = id
                _uiState.update { it.copy(permissionPrompt = PermissionPrompt.REQUEST_NOTIFICATIONS) }
            }
        }
    }

    /** Result of the system notification permission dialog started for [PermissionPrompt.REQUEST_NOTIFICATIONS]. */
    fun onNotificationPermissionResult(granted: Boolean) {
        val id = pendingNotifyIntakeId
        pendingNotifyIntakeId = null
        _uiState.update { it.copy(permissionPrompt = null) }
        if (granted && id != null) {
            updateIntake(id) { it.copy(notify = true) }
            explainExactAlarmsIfUseful()
        } else {
            notificationDenials++
            _uiState.update { it.copy(errorMessage = UiMessage(R.string.notifications_permission_denied)) }
        }
    }

    /** The screen has reacted to (or the user dismissed) the current [PermissionPrompt]. */
    fun onPermissionPromptHandled() = _uiState.update { it.copy(permissionPrompt = null) }

    private fun explainExactAlarmsIfUseful() {
        if (exactAlarmsExplained) return
        if (permissions.exactAlarmsNeedUserAccess() && !permissions.canScheduleExactAlarms()) {
            exactAlarmsExplained = true
            _uiState.update { it.copy(permissionPrompt = PermissionPrompt.EXPLAIN_EXACT_ALARMS) }
        }
    }

    private fun updateIntake(id: Int, transform: (IntakeDraft) -> IntakeDraft) = _uiState.update { state ->
        state.copy(
            intakes = state.intakes.map { if (it.id == id) transform(it) else it },
            errorMessage = null,
        )
    }

    // --- Save --------------------------------------------------------------------------------

    fun save() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading || current.loadFailed) return

        val frequencyDays = when (current.frequencyMode) {
            FrequencyMode.EVERY_DAY -> Medication.DEFAULT_FREQUENCY_DAYS
            // 0 is rejected by the use case's range check, which also produces the user-facing message.
            FrequencyMode.EVERY_N_DAYS -> current.frequencyDaysText.toIntOrNull() ?: 0
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val intakes = current.intakes.map { Intake(time = it.time, doseAmount = it.dose, notify = it.notify) }
            val outcome = runCatchingCancellable {
                updateDetails(
                    medicationId = medicationId,
                    nickname = current.nickname,
                    frequencyDays = frequencyDays,
                    intakes = intakes,
                )
            }
            outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is AppResult.Success -> {
                            _uiState.update { it.copy(isSaving = false) }
                            _saved.send(intakes.any { it.notify } && !permissions.canPostNotifications())
                        }
                        is AppResult.Failure -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = result.error.toUiMessage())
                        }
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isSaving = false, errorMessage = UiMessage(R.string.error_storage)) }
                },
            )
        }
    }

    private fun Intake.toDraft() = IntakeDraft(
        id = nextDraftId++,
        time = time,
        dose = doseAmount.orEmpty(),
        notify = notify,
    )

    private fun EditMedicationUiState.withError(reason: ValidationReason) =
        copy(errorMessage = DomainError.Validation(reason).toUiMessage())

    private companion object {
        const val DEFAULT_N_DAYS = 2
        val MAX_FREQUENCY_DIGITS = Medication.MAX_FREQUENCY_DAYS.toString().length
    }
}
