package com.example.dosediary.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.usecase.CancelReminderUseCase
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.DeleteSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ObserveSymptomsUseCase
import com.example.dosediary.domain.usecase.ScheduleReminderUseCase
import com.example.dosediary.domain.usecase.UpdateMedicationNicknameUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.format
import com.example.dosediary.presentation.common.runCatchingCancellable
import com.example.dosediary.presentation.common.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val medications: List<Medication>,
        val symptoms: List<Symptom>,
    ) : DashboardUiState

    data class Error(val error: DomainError) : DashboardUiState
}

class DashboardViewModel(
    observeMedications: ObserveSavedMedicationsUseCase,
    observeSymptoms: ObserveSymptomsUseCase,
    private val deleteMedication: DeleteMedicationUseCase,
    private val deleteSymptom: DeleteSymptomUseCase,
    private val scheduleReminder: ScheduleReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
    private val updateNickname: UpdateMedicationNicknameUseCase,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        combine(observeMedications(), observeSymptoms()) { medications, symptoms ->
            DashboardUiState.Success(medications, symptoms)
        }
            .map<DashboardUiState.Success, DashboardUiState> { it }
            .catch { emit(DashboardUiState.Error(DomainError.Storage)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

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
            val message = runCatchingCancellable { deleteMedication(medication.id) }.fold(
                onSuccess = { UiMessage(R.string.medication_removed, listOf(medication.displayName)) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }

    fun removeSymptom(symptom: Symptom) {
        viewModelScope.launch {
            val message = runCatchingCancellable { deleteSymptom(symptom.id) }.fold(
                onSuccess = { UiMessage(R.string.symptom_removed) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }
}
