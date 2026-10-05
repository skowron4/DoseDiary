package com.example.dosediary.presentation.interaction

import com.example.dosediary.domain.interaction.InteractionCheckResult
import com.example.dosediary.domain.interaction.InteractionSeverity
import com.example.dosediary.domain.interaction.InteractionWarning
import com.example.dosediary.domain.model.DomainError

/** What the interaction banner shows. */
sealed interface InteractionUiState {
    /** Nothing to check yet (or nothing worth showing). */
    data object Idle : InteractionUiState

    data object Checking : InteractionUiState

    /** The label was read and mentions none of the user's other medications. Not a safety guarantee. */
    data object NothingFound : InteractionUiState

    data class Warnings(val items: List<InteractionWarning>) : InteractionUiState {
        val highestSeverity: InteractionSeverity get() = items.maxOf { it.severity }
    }

    /**
     * The check could not run. [error] is set for connectivity/server problems (worth a retry), and
     * `null` when the label simply has no interaction information.
     */
    data class Unavailable(val error: DomainError?) : InteractionUiState
}

fun InteractionCheckResult.toUiState(): InteractionUiState = when (this) {
    InteractionCheckResult.NothingFound -> InteractionUiState.NothingFound
    is InteractionCheckResult.Warnings -> InteractionUiState.Warnings(items)
    is InteractionCheckResult.Unknown -> InteractionUiState.Unavailable(error)
}
