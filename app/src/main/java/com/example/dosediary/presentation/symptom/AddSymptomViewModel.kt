package com.example.dosediary.presentation.symptom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.R
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.domain.usecase.GetSymptomUseCase
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.presentation.common.UiMessage
import com.example.dosediary.presentation.common.runCatchingCancellable
import com.example.dosediary.presentation.common.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddSymptomUiState(
    val isEditing: Boolean = false,
    /** `true` while an existing symptom is being loaded for editing. */
    val isLoading: Boolean = false,
    /** The symptom to edit could not be found/loaded. */
    val loadFailed: Boolean = false,
    val medications: List<Medication> = emptyList(),
    val selectedMedicationId: String? = null,
    val severity: Int = DEFAULT_SEVERITY,
    val selectedTags: Set<SymptomTag> = emptySet(),
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: UiMessage? = null,
) {
    companion object {
        const val DEFAULT_SEVERITY = 5
    }
}

/**
 * Creates a new symptom entry, or edits an existing one when [symptomId] is non-null.
 *
 * @param initialMedicationId Medication preselected when launched from a medication card.
 */
class AddSymptomViewModel(
    initialMedicationId: String?,
    private val symptomId: Long?,
    observeMedications: ObserveSavedMedicationsUseCase,
    private val getSymptom: GetSymptomUseCase,
    private val logSymptom: LogSymptomUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddSymptomUiState(
            isEditing = symptomId != null,
            isLoading = symptomId != null,
            selectedMedicationId = initialMedicationId,
        ),
    )
    val uiState: StateFlow<AddSymptomUiState> = _uiState.asStateFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)

    /** Emits once after the entry was stored successfully. */
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    private var originalLoggedAtMillis: Long? = null

    init {
        viewModelScope.launch {
            observeMedications()
                .catch { /* Dropdown just stays empty; the user can still log a general symptom. */ }
                .collect { list -> _uiState.update { it.copy(medications = list) } }
        }
        if (symptomId != null) loadExisting(symptomId)
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val symptom = runCatchingCancellable { getSymptom(id) }.getOrNull()
            if (symptom == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                originalLoggedAtMillis = symptom.loggedAtMillis
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedMedicationId = symptom.medicationId,
                        severity = symptom.severity,
                        selectedTags = symptom.tags,
                        notes = symptom.notes,
                    )
                }
            }
        }
    }

    fun onMedicationSelected(id: String?) = _uiState.update { it.copy(selectedMedicationId = id, errorMessage = null) }

    fun onSeverityChange(value: Int) = _uiState.update {
        it.copy(severity = value.coerceIn(Symptom.MIN_SEVERITY, Symptom.MAX_SEVERITY), errorMessage = null)
    }

    /** Adds the tag if it is not selected, removes it otherwise. */
    fun onTagToggled(tag: SymptomTag) = _uiState.update {
        val updated = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag
        it.copy(selectedTags = updated, errorMessage = null)
    }

    fun onNotesChange(value: String) = _uiState.update {
        it.copy(notes = value.take(Symptom.MAX_NOTES_LENGTH), errorMessage = null)
    }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val outcome = runCatchingCancellable {
                logSymptom(
                    id = symptomId ?: 0,
                    medicationId = current.selectedMedicationId,
                    severity = current.severity,
                    notes = current.notes,
                    tags = current.selectedTags,
                    originalLoggedAtMillis = originalLoggedAtMillis,
                )
            }
            outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is AppResult.Success -> {
                            _uiState.update { it.copy(isSaving = false) }
                            _saved.send(Unit)
                        }
                        is AppResult.Failure -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = result.error.toUiMessage())
                        }
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isSaving = false, errorMessage = UiMessage(R.string.error_storage)) }
                },
            )
        }
    }
}
