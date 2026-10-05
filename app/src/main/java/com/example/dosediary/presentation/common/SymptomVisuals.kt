package com.example.dosediary.presentation.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.dosediary.R
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag

/** Anchor colors of the severity scale: low = green, middle = yellow, high = red. */
val SeverityLow = Color(0xFF4CAF50)
val SeverityMid = Color(0xFFFFC107)
val SeverityHigh = Color(0xFFF44336)

/** Position of [severity] on the scale, `0f` (minimum) .. `1f` (maximum). */
fun severityFraction(severity: Int): Float =
    ((severity - Symptom.MIN_SEVERITY).toFloat() / (Symptom.MAX_SEVERITY - Symptom.MIN_SEVERITY)).coerceIn(0f, 1f)

/** Smoothly interpolated green -> yellow -> red color for [severity]. */
fun severityColor(severity: Int): Color {
    val fraction = severityFraction(severity)
    return if (fraction < 0.5f) {
        lerp(SeverityLow, SeverityMid, fraction * 2f)
    } else {
        lerp(SeverityMid, SeverityHigh, (fraction - 0.5f) * 2f)
    }
}

/** A face that gets progressively more distressed as [severity] increases. */
fun severityEmoji(severity: Int): String = when {
    severity <= 2 -> "\uD83D\uDE0A" // smiling face
    severity <= 4 -> "\uD83D\uDE42" // slightly smiling face
    severity <= 6 -> "\uD83D\uDE10" // neutral face
    severity <= 8 -> "\uD83D\uDE23" // persevering face
    else -> "\uD83D\uDE2B" // tired face
}

@StringRes
fun severityLabelRes(severity: Int): Int = when {
    severity <= 3 -> R.string.severity_level_mild
    severity <= 7 -> R.string.severity_level_moderate
    else -> R.string.severity_level_severe
}

@StringRes
fun SymptomTag.labelRes(): Int = when (this) {
    SymptomTag.HEADACHE -> R.string.tag_headache
    SymptomTag.NAUSEA -> R.string.tag_nausea
    SymptomTag.FATIGUE -> R.string.tag_fatigue
    SymptomTag.FEVER -> R.string.tag_fever
    SymptomTag.DIZZINESS -> R.string.tag_dizziness
    SymptomTag.STOMACH_PAIN -> R.string.tag_stomach_pain
    SymptomTag.COUGH -> R.string.tag_cough
    SymptomTag.RASH -> R.string.tag_rash
    SymptomTag.INSOMNIA -> R.string.tag_insomnia
    SymptomTag.MUSCLE_PAIN -> R.string.tag_muscle_pain
}
