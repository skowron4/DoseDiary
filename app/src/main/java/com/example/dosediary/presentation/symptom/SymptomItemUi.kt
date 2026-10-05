package com.example.dosediary.presentation.symptom

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.dosediary.R
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.presentation.common.UiTextFormatter
import com.example.dosediary.presentation.common.labelRes
import com.example.dosediary.presentation.common.severityColor

/**
 * Everything a symptom card shows, as final strings (plus the badge color). Built off the main thread
 * by [SymptomItemUi.from]; the card itself only places text.
 *
 * @property tags At most [MAX_VISIBLE_TAGS] localised tag labels; the rest are summarised in [hiddenTagsText].
 * @property hiddenTagsText "+N" for the tags that are not listed, or `null` when all of them are.
 */
@Immutable
data class SymptomItemUi(
    val id: Long,
    /** "Related to: Morning pill" when the symptom is linked to a medication, otherwise the "General" label. */
    val title: String,
    val isLinkedToMedication: Boolean,
    val dateText: String,
    val severityText: String,
    val severityColor: Color,
    val tags: List<String>,
    val hiddenTagsText: String?,
    val notes: String?,
) {
    companion object {
        /**
         * The card shows tags in one simple row instead of measuring how many fit, so the number of
         * chips is decided here, once, rather than during layout of every scrolled-in item.
         */
        const val MAX_VISIBLE_TAGS = 3

        fun from(symptom: Symptom, text: UiTextFormatter): SymptomItemUi {
            val labels = symptom.tags.map { text.string(it.labelRes()) }
            val hidden = labels.size - MAX_VISIBLE_TAGS
            // The name is the medication's custom name when it has one, otherwise its commercial name.
            val medicationName = symptom.medicationName?.takeIf { it.isNotBlank() }
            return SymptomItemUi(
                id = symptom.id,
                title = if (medicationName != null) {
                    text.string(R.string.symptom_related_to, medicationName)
                } else {
                    text.string(R.string.symptom_general)
                },
                isLinkedToMedication = medicationName != null,
                dateText = text.dateTime(symptom.loggedAtMillis),
                severityText = symptom.severity.toString(),
                severityColor = severityColor(symptom.severity),
                tags = labels.take(MAX_VISIBLE_TAGS),
                hiddenTagsText = if (hidden > 0) text.string(R.string.symptom_tags_more, hidden) else null,
                notes = symptom.notes.takeIf { it.isNotBlank() },
            )
        }
    }
}
