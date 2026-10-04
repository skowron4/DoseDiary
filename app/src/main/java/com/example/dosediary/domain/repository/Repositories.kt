package com.example.dosediary.domain.repository

import com.example.dosediary.domain.model.AppLanguage
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Remote drug catalogue (OpenFDA). */
interface DrugSearchRepository {
    suspend fun searchByBrandName(query: String): AppResult<List<Medication>>
}

/** Medications the user has saved locally. */
interface MedicationRepository {
    fun observeMedications(): Flow<List<Medication>>
    suspend fun getMedication(id: String): Medication?
    suspend fun saveMedication(medication: Medication)
    suspend fun deleteMedication(id: String)
    suspend fun updateReminder(id: String, time: ReminderTime?)
    suspend fun updateNickname(id: String, nickname: String?)
}

/** Symptom journal. */
interface SymptomRepository {
    fun observeSymptoms(): Flow<List<Symptom>>
    suspend fun getSymptom(id: Long): Symptom?
    suspend fun saveSymptom(symptom: Symptom)
    suspend fun deleteSymptom(id: Long)
}

/** Simple key/value user preferences. */
interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setBiometricsEnabled(enabled: Boolean)
    suspend fun setScreenProtectionEnabled(enabled: Boolean)
}

/** Recently submitted search queries, most recent first. */
interface SearchHistoryRepository {
    fun observeHistory(): Flow<List<String>>
    suspend fun addQuery(query: String)
    suspend fun removeQuery(query: String)
    suspend fun clear()
}

/**
 * Per-app UI language. The platform owns persistence (Android 13+ system setting, or AppCompat's
 * auto-stored locales on older versions), so no extra storage is needed here.
 */
interface LanguageManager {
    fun getLanguage(): AppLanguage

    /** Applies [language] and recreates the visible activity so all text switches immediately. */
    fun setLanguage(language: AppLanguage)
}

/** Platform abstraction for scheduling local, repeating medication reminders. */
interface ReminderScheduler {
    fun schedule(medicationId: String, medicationName: String, time: ReminderTime)
    fun cancel(medicationId: String)
}
