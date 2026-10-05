package com.example.dosediary.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.UiTextFormatter
import com.example.dosediary.presentation.common.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /** [medications] are fully formatted; the list only places their strings. */
    data class Success(val medications: List<MedicationItemUi>) : DashboardUiState

    data class Error(val error: DomainError) : DashboardUiState
}

class DashboardViewModel(
    observeMedications: ObserveSavedMedicationsUseCase,
    private val deleteMedication: DeleteMedicationUseCase,
    private val text: UiTextFormatter,
    mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Changes when the app language does, so already-formatted strings are rebuilt. */
    private val localeKey = MutableStateFlow(text.localeKey())

    val uiState: StateFlow<DashboardUiState> =
        observeMedications()
            // Room re-queries on any write to the tables; skip emissions that did not change the list.
            .distinctUntilChanged()
            .combine(localeKey) { medications, _ ->
                // All string building / time formatting happens here, off the main thread.
                medications.map { MedicationItemUi.from(it, text) }
            }
            .flowOn(mappingDispatcher)
            .map<List<MedicationItemUi>, DashboardUiState> { DashboardUiState.Success(it) }
            .catch { emit(DashboardUiState.Error(DomainError.Storage)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** The screen calls this when the configuration changes; it is a no-op unless the language did. */
    fun onLocaleMaybeChanged() {
        localeKey.value = text.localeKey()
    }

    fun removeMedication(id: String, displayName: String) {
        viewModelScope.launch {
            val message = runCatchingCancellable { deleteMedication(id) }.fold(
                onSuccess = { UiMessage(R.string.medication_removed, listOf(displayName)) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }
}
