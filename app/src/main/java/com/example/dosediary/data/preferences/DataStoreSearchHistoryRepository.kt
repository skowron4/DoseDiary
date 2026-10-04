package com.example.dosediary.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.dosediary.domain.model.SearchHistory
import com.example.dosediary.domain.repository.SearchHistoryRepository
import com.example.dosediary.domain.usecase.SearchMedicationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Stores the history as a single newline-separated string. Queries are single-line (the search
 * field is `singleLine` and whitespace is normalised), so `\n` is a safe separator, and each update
 * happens inside [DataStore.edit], which makes the read-modify-write atomic.
 */
class DataStoreSearchHistoryRepository(
    private val dataStore: DataStore<Preferences>,
) : SearchHistoryRepository {

    override fun observeHistory(): Flow<List<String>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[HISTORY]) }
        .distinctUntilChanged()

    override suspend fun addQuery(query: String) {
        dataStore.edit { prefs ->
            prefs[HISTORY] = encode(
                SearchHistory.withEntry(decode(prefs[HISTORY]), query, SearchMedicationUseCase.MIN_QUERY_LENGTH),
            )
        }
    }

    override suspend fun removeQuery(query: String) {
        dataStore.edit { prefs ->
            prefs[HISTORY] = encode(SearchHistory.withoutEntry(decode(prefs[HISTORY]), query))
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(HISTORY) }
    }

    private fun decode(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    private fun encode(history: List<String>): String = history.joinToString(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\n"
        val HISTORY = stringPreferencesKey("search_history")
    }
}
