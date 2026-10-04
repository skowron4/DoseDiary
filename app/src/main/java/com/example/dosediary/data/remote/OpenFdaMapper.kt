package com.example.dosediary.data.remote

import com.example.dosediary.data.remote.dto.DrugLabelDto
import com.example.dosediary.domain.model.Medication

private const val MAX_SUMMARY_LENGTH = 220

/** Maps OpenFDA labels to domain medications, dropping unusable rows and de-duplicating by id. */
fun List<DrugLabelDto>.toDomain(): List<Medication> =
    mapNotNull { it.toDomainOrNull() }.distinctBy { it.id }

private fun DrugLabelDto.toDomainOrNull(): Medication? {
    val id = (id ?: setId)?.takeIf { it.isNotBlank() } ?: return null
    val brand = openfda?.brandName?.firstNotBlank() ?: return null
    return Medication(
        id = id,
        commercialName = brand.toDisplayCase(),
        // substance_name is the actual active ingredient; generic_name is a fallback for sparse labels.
        activeSubstance = (openfda.substanceName?.firstNotBlank() ?: openfda.genericName?.firstNotBlank())
            ?.toDisplayCase(),
        manufacturer = openfda.manufacturerName?.firstNotBlank(),
        purpose = (purpose ?: indicationsAndUsage)?.firstNotBlank()?.toSummary(),
        route = openfda.route?.firstNotBlank()?.toDisplayCase(),
    )
}

private fun List<String>.firstNotBlank(): String? = firstOrNull { it.isNotBlank() }?.trim()

/** OpenFDA frequently returns SHOUTING names; convert them to a friendlier case. */
private fun String.toDisplayCase(): String =
    if (any { it.isLowerCase() }) {
        this
    } else {
        lowercase().split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }
    }

private fun String.toSummary(): String {
    val cleaned = replace(Regex("\\s+"), " ")
        .replace(Regex("^(purpose|indications?( (and|&) usage)?)s?:?\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
    return if (cleaned.length <= MAX_SUMMARY_LENGTH) cleaned else cleaned.take(MAX_SUMMARY_LENGTH).trimEnd() + "…"
}
