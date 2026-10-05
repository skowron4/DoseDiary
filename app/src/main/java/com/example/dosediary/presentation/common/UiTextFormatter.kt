package com.example.dosediary.presentation.common

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.example.dosediary.domain.model.ReminderTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Turns domain values into the final, localised strings that list items display.
 *
 * ViewModels use this while mapping domain models to UI models, so a lazy list item never has to look
 * up a resource, build a string or format a date while it is being composed (which happens for every
 * item that scrolls into view).
 *
 * Implementations must use the *current* default locale on every call, because the user can switch
 * the app language while a ViewModel is still alive.
 */
interface UiTextFormatter {
    fun string(@StringRes id: Int, vararg args: Any): String

    /** Medium date + short time, e.g. "Oct 4, 2025, 5:46 PM". */
    fun dateTime(epochMillis: Long): String

    /** Short localised time of day, e.g. "8:00 AM" or "08:00". */
    fun time(time: ReminderTime): String

    /** Identifies the locale used for the strings above; a change means cached UI models are stale. */
    fun localeKey(): String
}

class AndroidUiTextFormatter(context: Context) : UiTextFormatter {

    private val appContext = context.applicationContext

    @Volatile
    private var cache: Pair<Locale, Context>? = null

    /** A context whose resources match the current default locale (the app context can lag behind). */
    private fun localizedContext(): Context {
        val locale = Locale.getDefault()
        cache?.takeIf { it.first == locale }?.let { return it.second }
        val configuration = Configuration(appContext.resources.configuration).apply { setLocale(locale) }
        return appContext.createConfigurationContext(configuration).also { cache = locale to it }
    }

    override fun string(@StringRes id: Int, vararg args: Any): String = localizedContext().getString(id, *args)

    override fun dateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )

    override fun time(time: ReminderTime): String =
        LocalTime.of(time.hour, time.minute).format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )

    override fun localeKey(): String = Locale.getDefault().toLanguageTag()
}
