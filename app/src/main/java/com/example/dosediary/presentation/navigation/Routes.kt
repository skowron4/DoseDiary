package com.example.dosediary.presentation.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable data object DashboardRoute

@Serializable data object SettingsRoute

/** Details and settings (reminder, nickname, removal) of one saved medication. */
@Serializable
data class MedicationDetailsRoute(val medicationId: String)

/** Full-screen search overlay (recent searches, saved matches and OpenFDA results). */
@Serializable data object SearchRoute

/**
 * @param medicationId Medication to preselect when logging a new symptom.
 * @param symptomId Existing symptom to edit; `null` creates a new one.
 */
@Serializable
data class AddSymptomRoute(
    val medicationId: String? = null,
    val symptomId: Long? = null,
)
