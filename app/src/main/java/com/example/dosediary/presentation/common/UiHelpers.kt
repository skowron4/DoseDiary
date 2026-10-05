package com.example.dosediary.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.dosediary.R
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A string resource plus arguments; lets ViewModels emit text without holding a Context. */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}

@StringRes
fun DomainError.messageRes(): Int = when (this) {
    DomainError.NoInternet -> R.string.error_no_internet
    DomainError.Timeout -> R.string.error_timeout
    is DomainError.Server -> R.string.error_server
    DomainError.Parsing -> R.string.error_parsing
    is DomainError.Validation -> when (reason) {
        ValidationReason.QUERY_TOO_SHORT -> R.string.error_query_too_short
        ValidationReason.SEVERITY_OUT_OF_RANGE -> R.string.error_severity_range
        ValidationReason.NOTES_TOO_LONG -> R.string.error_notes_too_long
        ValidationReason.NICKNAME_TOO_LONG -> R.string.error_nickname_too_long
        ValidationReason.DOSE_TOO_LONG -> R.string.error_dose_too_long
        ValidationReason.INTERVAL_OUT_OF_RANGE -> R.string.error_interval_range
        ValidationReason.TOO_MANY_REMINDERS -> R.string.error_too_many_reminders
        ValidationReason.MEDICATION_NOT_FOUND -> R.string.error_medication_not_found
    }
    DomainError.Storage -> R.string.error_storage
    DomainError.Unknown -> R.string.error_unknown
}

fun DomainError.toUiMessage(): UiMessage = UiMessage(
    res = messageRes(),
    args = if (this is DomainError.Server) listOf(code) else emptyList(),
)

/** Like [runCatching] but never swallows coroutine cancellation. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun ReminderTime.format(): String = LocalTime.of(hour, minute).format(timeFormatter)
