package com.example.dosediary.data.repository

import com.example.dosediary.data.remote.OpenFdaApi
import com.example.dosediary.data.remote.interactionText
import com.example.dosediary.data.remote.safeNetworkCall
import com.example.dosediary.domain.interaction.DrugInteractionRepository
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the `drug_interactions` label section from OpenFDA.
 *
 * Successful lookups (including "this label has no interaction section") are cached in memory for the
 * lifetime of the process, because label text does not change while the app runs and the same
 * medication is typically checked repeatedly (every time it is opened for editing). Failures are never
 * cached, so going back online fixes the next check.
 */
class DrugInteractionRepositoryImpl(
    private val api: OpenFdaApi,
    private val ioDispatcher: CoroutineDispatcher,
) : DrugInteractionRepository {

    /** Empty string stands for "label has no interaction text" ([ConcurrentHashMap] forbids nulls). */
    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun getInteractionText(medication: Medication): AppResult<String?> {
        cache[medication.id]?.let { return AppResult.Success(it.ifEmpty { null }) }

        return withContext(ioDispatcher) {
            safeNetworkCall { api.getLabelById(medication.id) }
                .map { response -> response.results.firstOrNull()?.interactionText() }
        }.also { result ->
            if (result is AppResult.Success) cache[medication.id] = result.data.orEmpty()
        }
    }
}
