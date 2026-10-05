package com.example.dosediary.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.usecase.CancelReminderUseCase
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ScheduleReminderUseCase
import com.example.dosediary.domain.usecase.UpdateMedicationNicknameUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.format
import com.example.dosediary.presentation.common.runCatchingCancellable
import com.example.dosediary.presentation.common.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MedicationDetailsUiState {
    data object Loading : MedicationDetailsUiState
    data class Success(val medication: Medication) : MedicationDetailsUiState

    /** The medication is not (or no longer) in the user's diary. */
    data object NotFound : MedicationDetailsUiState
    data class Error(val error: DomainError) : MedicationDetailsUiState
}

/** Details and settings (reminder, nickname, removal) of one saved medication. */
class MedicationDetailsViewModel(
    private val medicationId: String,
    observeMedications: ObserveSavedMedicationsUseCase,
    private val scheduleReminder: ScheduleReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
    private val updateNickname: UpdateMedicationNicknameUseCase,
    private val deleteMedication: DeleteMedicationUseCase,
) : ViewModel() {

    /** Set once the medication was removed, so the screen does not flash a "not found" state. */
    private val isDeleted = MutableStateFlow(false)

    val uiState: StateFlow<MedicationDetailsUiState> = combine(
        observeMedications()
            .map<List<Medication>, MedicationDetailsUiState> { list ->
                list.firstOrNull { it.id == medicationId }
                    ?.let { MedicationDetailsUiState.Success(it) }
                    ?: MedicationDetailsUiState.NotFound
            }
            .catch { emit(MedicationDetailsUiState.Error(DomainError.Storage)) },
        isDeleted,
    ) { state, deleted -> if (deleted) MedicationDetailsUiState.Loading else state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationDetailsUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _deleted = Channel<Unit>(Channel.CONFLATED)

    /** Emits once after the medication was removed; the screen should then close. */
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    fun setReminder(medication: Medication, time: ReminderTime, notificationsAllowed: Boolean) {
        viewModelScope.launch {
            val message = runCatchingCancellable { scheduleReminder(medication.id, time) }.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is AppResult.Failure -> outcome.error.toUiMessage()
                        is AppResult.Success -> if (notificationsAllowed) {
                            UiMessage(R.string.reminder_set, listOf(medication.displayName, time.format()))
                        } else {
                            UiMessage(R.string.reminder_set_no_permission, listOf(time.format()))
                        }
                    }
                },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }

    fun clearReminder(medication: Medication) {
        viewModelScope.launch {
            val message = runCatchingCancellable { cancelReminder(medication.id) }.fold(
                onSuccess = { UiMessage(R.string.reminder_cleared, listOf(medication.displayName)) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }

    /** Sets the nickname; a blank [nickname] clears it. */
    fun setNickname(medication: Medication, nickname: String) {
        viewModelScope.launch {
            val message = runCatchingCancellable { updateNickname(medication.id, nickname) }.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is AppResult.Failure -> outcome.error.toUiMessage()
                        is AppResult.Success -> UiMessage(R.string.nickname_saved)
                    }
                },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }

    fun removeMedication(medication: Medication) {
        viewModelScope.launch {
            runCatchingCancellable { deleteMedication(medication.id) }.fold(
                onSuccess = {
                    isDeleted.value = true
                    _deleted.send(Unit)
                },
                onFailure = { _messages.send(UiMessage(R.string.error_storage)) },
            )
        }
    }
}
