package com.example.dosediary.presentation.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable data object DashboardRoute

@Serializable data object SettingsRoute

/**
 * @param medicationId Medication to preselect when logging a new symptom.
 * @param symptomId Existing symptom to edit; `null` creates a new one.
 */
@Serializable
data class AddSymptomRoute(
    val medicationId: String? = null,
    val symptomId: Long? = null,
)
