package com.example.dosediary.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.runCatchingCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val medications: List<Medication>,
    ) : DashboardUiState

    data class Error(val error: DomainError) : DashboardUiState
}

class DashboardViewModel(
    observeMedications: ObserveSavedMedicationsUseCase,
    private val deleteMedication: DeleteMedicationUseCase,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        observeMedications()
            // Room re-queries on any write to the tables; skip emissions that did not change the list.
            .distinctUntilChanged()
            .map<List<Medication>, DashboardUiState> { DashboardUiState.Success(it) }
            .catch { emit(DashboardUiState.Error(DomainError.Storage)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun removeMedication(medication: Medication) {
        viewModelScope.launch {
            val message = runCatchingCancellable { deleteMedication(medication.id) }.fold(
                onSuccess = { UiMessage(R.string.medication_removed, listOf(medication.displayName)) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }
}
