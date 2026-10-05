package com.example.dosediary.presentation.symptom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.usecase.DeleteSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSymptomsUseCase
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

sealed interface SymptomDiaryUiState {
    data object Loading : SymptomDiaryUiState

    /** [symptoms] are fully formatted; the list only places their strings. */
    data class Success(val symptoms: List<SymptomItemUi>) : SymptomDiaryUiState

    data class Error(val error: DomainError) : SymptomDiaryUiState
}

/** The chronological symptom log (newest first) with delete support. */
class SymptomDiaryViewModel(
    observeSymptoms: ObserveSymptomsUseCase,
    private val deleteSymptom: DeleteSymptomUseCase,
    private val text: UiTextFormatter,
    mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Changes when the app language does, so already-formatted strings are rebuilt. */
    private val localeKey = MutableStateFlow(text.localeKey())

    val uiState: StateFlow<SymptomDiaryUiState> = observeSymptoms()
        // Room re-queries on any write to the table; skip emissions that did not change the list.
        .distinctUntilChanged()
        .combine(localeKey) { symptoms, _ ->
            // Dates, tag labels and the "+N" text are built here, off the main thread.
            symptoms.map { SymptomItemUi.from(it, text) }
        }
        .flowOn(mappingDispatcher)
        .map<List<SymptomItemUi>, SymptomDiaryUiState> { SymptomDiaryUiState.Success(it) }
        .catch { emit(SymptomDiaryUiState.Error(DomainError.Storage)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SymptomDiaryUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** The screen calls this when the configuration changes; it is a no-op unless the language did. */
    fun onLocaleMaybeChanged() {
        localeKey.value = text.localeKey()
    }

    fun removeSymptom(id: Long) {
        viewModelScope.launch {
            val message = runCatchingCancellable { deleteSymptom(id) }.fold(
                onSuccess = { UiMessage(R.string.symptom_removed) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }
}
