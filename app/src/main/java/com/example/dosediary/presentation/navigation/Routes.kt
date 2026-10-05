package com.example.dosediary.presentation.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable data object DashboardRoute

/** Symptom diary tab: the chronological symptom log. */
@Serializable data object SymptomDiaryRoute

@Serializable data object SettingsRoute

/** Full-screen search overlay (recent searches, saved matches and OpenFDA results). */
@Serializable data object SearchRoute

/** Edit form (nickname, dosage, reminders) of a saved medication. */
@Serializable
data class EditMedicationRoute(val medicationId: String)

/**
 * @param medicationId Medication to preselect when logging a new symptom.
 * @param symptomId Existing symptom to edit; `null` creates a new one.
 */
@Serializable
data class AddSymptomRoute(
    val medicationId: String? = null,
    val symptomId: Long? = null,
)
