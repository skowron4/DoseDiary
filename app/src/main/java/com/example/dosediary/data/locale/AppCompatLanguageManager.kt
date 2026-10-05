package com.example.dosediary.data.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.dosediary.domain.model.AppLanguage
import com.example.dosediary.domain.repository.LanguageManager

/**
 * [LanguageManager] backed by AndroidX's per-app locale API. On Android 13+ this is the system
 * "App languages" setting; below that, `AppLocalesMetadataHolderService` (see the manifest) makes
 * AppCompat persist the choice itself.
 */
class AppCompatLanguageManager : LanguageManager {

    override fun getLanguage(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }

    override fun setLanguage(language: AppLanguage) {
        val locales = language.languageTag
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
