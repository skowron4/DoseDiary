package com.example.dosediary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of the OpenFDA drug label response we care about.
 * Every field is optional/defaulted because OpenFDA labels are notoriously inconsistent.
 */
@Serializable
data class DrugLabelResponseDto(
    val results: List<DrugLabelDto> = emptyList(),
)

@Serializable
data class DrugLabelDto(
    val id: String? = null,
    @SerialName("set_id") val setId: String? = null,
    val openfda: OpenFdaDto? = null,
    val purpose: List<String>? = null,
    @SerialName("indications_and_usage") val indicationsAndUsage: List<String>? = null,
    @SerialName("drug_interactions") val drugInteractions: List<String>? = null,
)

@Serializable
data class OpenFdaDto(
    @SerialName("brand_name") val brandName: List<String>? = null,
    @SerialName("generic_name") val genericName: List<String>? = null,
    @SerialName("substance_name") val substanceName: List<String>? = null,
    @SerialName("manufacturer_name") val manufacturerName: List<String>? = null,
    val route: List<String>? = null,
)
