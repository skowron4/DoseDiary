package com.example.dosediary.i18n

import com.example.dosediary.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `plain and regional tags map to the language`() {
        assertEquals(AppLanguage.POLISH, AppLanguage.fromTag("pl"))
        assertEquals(AppLanguage.POLISH, AppLanguage.fromTag("pl-PL"))
        assertEquals(AppLanguage.POLISH, AppLanguage.fromTag("pl_PL"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN-us"))
    }

    @Test
    fun `missing or unsupported tags fall back to following the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de"))
    }

    @Test
    fun `system language has no tag`() {
        assertEquals(null, AppLanguage.SYSTEM.languageTag)
    }
}
