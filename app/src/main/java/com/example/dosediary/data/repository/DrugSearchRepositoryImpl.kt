package com.example.dosediary.data.repository

import com.example.dosediary.data.remote.OpenFdaApi
import com.example.dosediary.data.remote.safeNetworkCall
import com.example.dosediary.data.remote.toDomain
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.map
import com.example.dosediary.domain.repository.DrugSearchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class DrugSearchRepositoryImpl(
    private val api: OpenFdaApi,
    private val ioDispatcher: CoroutineDispatcher,
) : DrugSearchRepository {

    override suspend fun searchByBrandName(query: String): AppResult<List<Medication>> =
        withContext(ioDispatcher) {
            // Mapping/parsing happens off the main thread too.
            safeNetworkCall { api.searchByBrandName(query) }
                .map { response -> response.results.toDomain() }
        }
}
