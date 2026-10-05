package com.example.dosediary.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.InteractionCheckResult
import com.example.dosediary.domain.interaction.InteractionWarning
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
    /** Ids of results whose interaction check is running; their save button is disabled. */
    val checkingIds: Set<String> = emptySet(),
    /** Set when saving found possible interactions and the user has to decide. */
    val pendingInteraction: PendingInteraction? = null,
)

/** A medication waiting for the user's "Save anyway" / "Cancel" after interaction warnings were found. */
data class PendingInteraction(
    val medication: Medication,
    val warnings: List<InteractionWarning>,
)

/** Interaction-check bookkeeping, kept in one flow so it combines as a single input. */
private data class InteractionFlowState(
    val checkingIds: Set<String> = emptySet(),
    val pending: PendingInteraction? = null,
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
    private val checkInteractions: CheckMedicationInteractionsUseCase,
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

    private val interactionState = MutableStateFlow(InteractionFlowState())

    val uiState: StateFlow<SearchUiState> = combine(
        resultState.onStart { emit(SearchResultState.Idle) },
        savedMedications,
        _query,
        history,
        interactionState,
    ) { result, saved, query, recent, interaction ->
        SearchUiState(
            result = result,
            savedIds = saved.mapTo(HashSet()) { it.id },
            localMatches = if (query.isBlank()) emptyList() else saved.filter { it.matchesQuery(query) },
            history = recent,
            checkingIds = interaction.checkingIds,
            pendingInteraction = interaction.pending,
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

    /**
     * Single entry point through which a search result is added to the diary. It first checks the
     * medication against the ones already saved:
     *  - possible interactions found -> wait for the user ([confirmSaveDespiteInteractions] / [dismissInteractionWarning]);
     *  - nothing found -> save;
     *  - check impossible (offline, timeout, no label text) -> save anyway, so the app stays usable
     *    offline, and say that the check did not happen.
     */
    fun save(medication: Medication) {
        if (medication.id in interactionState.value.checkingIds || interactionState.value.pending != null) return

        // Saving a result is a strong signal the query was useful.
        recordCurrentQuery()
        viewModelScope.launch {
            interactionState.update { it.copy(checkingIds = it.checkingIds + medication.id) }
            val result = runCatchingCancellable { checkInteractions(medication) }
                .getOrElse { InteractionCheckResult.Unknown(DomainError.Unknown) }
            interactionState.update { it.copy(checkingIds = it.checkingIds - medication.id) }

            when (result) {
                is InteractionCheckResult.Warnings -> interactionState.update {
                    it.copy(pending = PendingInteraction(medication, result.items))
                }
                is InteractionCheckResult.Unknown -> persist(medication, interactionsUnchecked = result.error != null)
                InteractionCheckResult.NothingFound -> persist(medication, interactionsUnchecked = false)
            }
        }
    }

    /** The user saw the warnings and still wants the medication in the diary. */
    fun confirmSaveDespiteInteractions() {
        val pending = interactionState.value.pending ?: return
        interactionState.update { it.copy(pending = null) }
        viewModelScope.launch { persist(pending.medication, interactionsUnchecked = false) }
    }

    fun dismissInteractionWarning() {
        interactionState.update { it.copy(pending = null) }
    }

    private suspend fun persist(medication: Medication, interactionsUnchecked: Boolean) {
        val message = runCatchingCancellable { saveMedication(medication) }.fold(
            onSuccess = {
                val text = if (interactionsUnchecked) R.string.medication_saved_unchecked else R.string.medication_saved
                UiMessage(text, listOf(medication.displayName))
            },
            onFailure = { UiMessage(R.string.error_storage) },
        )
        _messages.send(message)
    }
    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
