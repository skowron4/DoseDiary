package com.example.dosediary.domain.model

/**
 * Languages the user can pick for the app UI.
 *
 * To add a language: add a constant here, a `res/values-<tag>/strings.xml`, and the tag to
 * `res/xml/locales_config.xml`. `StringResourcesTest` fails if a translation file is incomplete.
 *
 * @property languageTag BCP-47 tag, or `null` for "follow the system language".
 */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    POLISH("pl"),
    ;

    companion object {
        /** Maps a stored/reported tag (e.g. `"pl"` or `"pl-PL"`) to a supported language, else [SYSTEM]. */
        fun fromTag(tag: String?): AppLanguage {
            val primary = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            if (primary.isNullOrEmpty()) return SYSTEM
            return entries.firstOrNull { it.languageTag == primary } ?: SYSTEM
        }
    }
}
