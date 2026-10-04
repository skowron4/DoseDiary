package com.example.dosediary.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.matchesQuery
import com.example.dosediary.domain.usecase.AddSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ClearSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ObserveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.RemoveSearchHistoryUseCase
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
    /** Saved medications matching the current query (empty while the query is blank). */
    val localMatches: List<Medication> = emptyList(),
    /** Recent queries, most recent first. Shown while the query is blank. */
    val history: List<String> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchMedication: SearchMedicationUseCase,
    observeSavedMedications: ObserveSavedMedicationsUseCase,
    private val saveMedication: SaveMedicationUseCase,
    observeHistory: ObserveSearchHistoryUseCase,
    private val addToHistory: AddSearchHistoryUseCase,
    private val removeFromHistory: RemoveSearchHistoryUseCase,
    private val clearHistory: ClearSearchHistoryUseCase,
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

    private val savedMedications: Flow<List<Medication>> = observeSavedMedications()
        .catch { emit(emptyList()) }

    private val history: Flow<List<String>> = observeHistory()
        .catch { emit(emptyList()) }

    val uiState: StateFlow<SearchUiState> = combine(
        resultState.onStart { emit(SearchResultState.Idle) },
        savedMedications,
        _query,
        history,
    ) { result, saved, query, recent ->
        SearchUiState(
            result = result,
            savedIds = saved.mapTo(HashSet()) { it.id },
            localMatches = if (query.isBlank()) emptyList() else saved.filter { it.matchesQuery(query) },
            history = recent,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.update { value }
    }

    /** The user submitted the query (keyboard "search" action): remember it. */
    fun onSearchSubmitted() {
        recordCurrentQuery()
    }

    /** A history row was tapped: run that query again and bump it to the top. */
    fun onHistorySelected(query: String) {
        _query.update { query }
        viewModelScope.launch { runCatchingCancellable { addToHistory(query) } }
    }

    fun removeHistoryEntry(query: String) {
        viewModelScope.launch { runCatchingCancellable { removeFromHistory(query) } }
    }

    fun clearAllHistory() {
        viewModelScope.launch { runCatchingCancellable { clearHistory() } }
    }

    private fun recordCurrentQuery() {
        val query = _query.value
        viewModelScope.launch { runCatchingCancellable { addToHistory(query) } }
    }

    fun retry() {
        retryTick.update { it + 1 }
    }

    fun save(medication: Medication) {
        // Saving a result is a strong signal the query was useful.
        recordCurrentQuery()
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
