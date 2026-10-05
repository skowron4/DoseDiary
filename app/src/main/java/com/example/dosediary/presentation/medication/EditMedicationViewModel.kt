package com.example.dosediary.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.InteractionCheckResult
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
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

data class EditMedicationUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val commercialName: String = "",
    val activeSubstance: String? = null,
    val nickname: String = "",
    val doseAmount: String = "",
    /** Raw text of the interval field; parsed on save so the user can type freely. */
    val intervalText: String = "",
    val reminderTimes: List<ReminderTime> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: UiMessage? = null,
    /** Possible interactions with the user's other saved medications (fail-open, never blocks editing). */
    val interactions: InteractionUiState = InteractionUiState.Idle,
)

/** Edits the nickname, dosage and daily reminder times of one saved medication. */
class EditMedicationViewModel(
    private val medicationId: String,
    private val getMedication: GetMedicationUseCase,
    private val updateDetails: UpdateMedicationDetailsUseCase,
    private val checkInteractions: CheckMedicationInteractionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditMedicationUiState())
    val uiState: StateFlow<EditMedicationUiState> = _uiState.asStateFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)

    /** Emits once after the changes were stored successfully. */
    val saved: Flow<Unit> = _saved.receiveAsFlow()

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
                        doseAmount = medication.doseAmount.orEmpty(),
                        intervalText = medication.intervalHours?.toString().orEmpty(),
                        reminderTimes = medication.reminderTimes,
                    )
                }
                loadedMedication = medication
                runInteractionCheck(medication)
            }
        }
    }

    private var loadedMedication: Medication? = null
    private var interactionJob: Job? = null

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

    fun onDoseAmountChange(value: String) = _uiState.update {
        it.copy(doseAmount = value.take(Medication.MAX_DOSE_LENGTH), errorMessage = null)
    }

    fun onIntervalChange(value: String) = _uiState.update {
        // Digits only; at most the width of MAX_INTERVAL_HOURS.
        it.copy(intervalText = value.filter(Char::isDigit).take(MAX_INTERVAL_DIGITS), errorMessage = null)
    }

    fun onAddReminder(time: ReminderTime) = _uiState.update {
        if (it.reminderTimes.size >= Medication.MAX_REMINDERS) {
            it.copy(errorMessage = DomainError.Validation(ValidationReason.TOO_MANY_REMINDERS).toUiMessage())
        } else {
            it.copy(reminderTimes = (it.reminderTimes + time).distinct().sorted(), errorMessage = null)
        }
    }

    fun onRemoveReminder(time: ReminderTime) = _uiState.update {
        it.copy(reminderTimes = it.reminderTimes - time, errorMessage = null)
    }

    fun save() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading || current.loadFailed) return

        val interval = current.intervalText.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (current.intervalText.isNotEmpty() && interval == null) {
            _uiState.update {
                it.copy(errorMessage = DomainError.Validation(ValidationReason.INTERVAL_OUT_OF_RANGE).toUiMessage())
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val outcome = runCatchingCancellable {
                updateDetails(
                    medicationId = medicationId,
                    nickname = current.nickname,
                    doseAmount = current.doseAmount,
                    intervalHours = interval,
                    reminderTimes = current.reminderTimes,
                )
            }
            outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is AppResult.Success -> {
                            _uiState.update { it.copy(isSaving = false) }
                            _saved.send(Unit)
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

    private companion object {
        val MAX_INTERVAL_DIGITS = Medication.MAX_INTERVAL_HOURS.toString().length
    }
}
