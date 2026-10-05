package com.example.dosediary.presentation.symptom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.usecase.DeleteSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSymptomsUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.runCatchingCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SymptomDiaryUiState {
    data object Loading : SymptomDiaryUiState
    data class Success(val symptoms: List<Symptom>) : SymptomDiaryUiState
    data class Error(val error: DomainError) : SymptomDiaryUiState
}

/** The chronological symptom log (newest first) with delete support. */
class SymptomDiaryViewModel(
    observeSymptoms: ObserveSymptomsUseCase,
    private val deleteSymptom: DeleteSymptomUseCase,
) : ViewModel() {

    val uiState: StateFlow<SymptomDiaryUiState> = observeSymptoms()
        .map<List<Symptom>, SymptomDiaryUiState> { SymptomDiaryUiState.Success(it) }
        .catch { emit(SymptomDiaryUiState.Error(DomainError.Storage)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SymptomDiaryUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

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
