package com.example.dosediary.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.SaveMedicationUseCase
import com.example.dosediary.domain.usecase.SearchMedicationUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.runCatchingCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the remote search request. */
sealed interface SearchResultState {
    /** Nothing to search for yet (query too short). */
    data object Idle : SearchResultState
    data object Loading : SearchResultState
    data class Success(val medications: List<Medication>) : SearchResultState
    data class Error(val error: DomainError) : SearchResultState
}

data class SearchUiState(
    val result: SearchResultState = SearchResultState.Idle,
    /** Ids of medications already saved, so rows can show "Saved". */
    val savedIds: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchMedication: SearchMedicationUseCase,
    observeSavedMedications: ObserveSavedMedicationsUseCase,
    private val saveMedication: SaveMedicationUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** Exposed separately from [uiState] so the text field is driven synchronously (no cursor jumps). */
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped to re-run the current query after a failure. */
    private val retryTick = MutableStateFlow(0)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val resultState: Flow<SearchResultState> = _query
        .map { it.trim() }
        .debounce(DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .combine(retryTick) { query, _ -> query }
        .flatMapLatest<String, SearchResultState> { query ->
            if (query.length < SearchMedicationUseCase.MIN_QUERY_LENGTH) {
                flowOf(SearchResultState.Idle)
            } else {
                flow {
                    emit(SearchResultState.Loading)
                    emit(
                        when (val result = searchMedication(query)) {
                            is AppResult.Success -> SearchResultState.Success(result.data)
                            is AppResult.Failure -> SearchResultState.Error(result.error)
                        },
                    )
                }
            }
        }

    private val savedIds: Flow<Set<String>> = observeSavedMedications()
        .map { list -> list.mapTo(HashSet()) { it.id } as Set<String> }
        .catch { emit(emptySet()) }

    val uiState: StateFlow<SearchUiState> = combine(
        resultState.onStart { emit(SearchResultState.Idle) },
        savedIds,
    ) { result, saved -> SearchUiState(result, saved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.update { value }
    }

    fun retry() {
        retryTick.update { it + 1 }
    }

    fun save(medication: Medication) {
        viewModelScope.launch {
            val message = runCatchingCancellable { saveMedication(medication) }.fold(
                onSuccess = { UiMessage(R.string.medication_saved, listOf(medication.displayName)) },
                onFailure = { UiMessage(R.string.error_storage) },
            )
            _messages.send(message)
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
