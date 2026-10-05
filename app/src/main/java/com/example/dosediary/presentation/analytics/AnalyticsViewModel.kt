package com.example.dosediary.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.domain.analytics.AnalyticsTimeline
import com.example.dosediary.domain.analytics.ObserveAnalyticsTimelineUseCase
import com.example.dosediary.domain.analytics.TimelineRange
import com.example.dosediary.domain.model.DomainError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/*
 * DRAFT (Phase 5): state + ViewModel for the future Analytics screen.
 *
 * Not registered in Koin and not reachable from navigation. To activate:
 *  - implement and bind MedicationCourseRepository (see its KDoc),
 *  - `factory { ObserveAnalyticsTimelineUseCase(get(), get(), get()) }`,
 *  - `viewModel { AnalyticsViewModel(observeTimeline = get()) }`,
 *  - add an AnalyticsRoute + bottom-bar destination and a Canvas chart that draws
 *    `timeline.medicationBars` (lanes) under `timeline.dailySeverity` / `symptomPoints`,
 *    using `timeline.window.fractionOf(time)` for the x-axis.
 */

sealed interface AnalyticsUiState {
    /** Which range chip is selected; available in every state so the chips never disappear. */
    val range: TimelineRange

    data class Loading(override val range: TimelineRange) : AnalyticsUiState

    /** Nothing logged (no symptoms and no courses) inside the selected range. */
    data class Empty(override val range: TimelineRange) : AnalyticsUiState

    data class Success(
        override val range: TimelineRange,
        val timeline: AnalyticsTimeline,
    ) : AnalyticsUiState

    data class Error(override val range: TimelineRange, val error: DomainError) : AnalyticsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    observeTimeline: ObserveAnalyticsTimelineUseCase,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(DEFAULT_RANGE)

    val uiState: StateFlow<AnalyticsUiState> = selectedRange
        .flatMapLatest { range ->
            observeTimeline(range)
                .map<AnalyticsTimeline, AnalyticsUiState> { timeline ->
                    if (timeline.isEmpty) AnalyticsUiState.Empty(range) else AnalyticsUiState.Success(range, timeline)
                }
                .onStart { emit(AnalyticsUiState.Loading(range)) }
                .catch { emit(AnalyticsUiState.Error(range, DomainError.Storage)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState.Loading(DEFAULT_RANGE))

    val range: StateFlow<TimelineRange> = selectedRange.asStateFlow()

    fun onRangeSelected(range: TimelineRange) {
        selectedRange.update { range }
    }

    private companion object {
        val DEFAULT_RANGE = TimelineRange.MONTH
    }
}
