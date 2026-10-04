package com.example.dosediary.data.remote

import com.example.dosediary.data.remote.dto.DrugLabelResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

/** Thin wrapper over `https://api.fda.gov/drug/label.json`. */
class OpenFdaApi(private val client: HttpClient) {

    /**
     * Searches drug labels by brand name.
     *
     * OpenFDA answers `404 NOT_FOUND` when a search has no hits; that is a valid "empty" result here,
     * not an error. Every other failure propagates as a Ktor/IO exception to be mapped by the caller.
     */
    suspend fun searchByBrandName(query: String, limit: Int = DEFAULT_LIMIT): DrugLabelResponseDto {
        val sanitized = query.sanitizeForSearch()
        if (sanitized.isEmpty()) return DrugLabelResponseDto()
        return try {
            client.get(ENDPOINT) {
                // Quoted so multi-word brand names are matched as a phrase.
                parameter("search", "openfda.brand_name:\"$sanitized\"")
                parameter("limit", limit)
            }.body()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) DrugLabelResponseDto() else throw e
        }
    }

    /** Strips characters that have special meaning in OpenFDA's (Elasticsearch) query syntax. */
    private fun String.sanitizeForSearch(): String =
        replace(Regex("[^\\p{L}\\p{N} \\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        const val BASE_URL = "https://api.fda.gov"
        private const val ENDPOINT = "$BASE_URL/drug/label.json"
        const val DEFAULT_LIMIT = 10
    }
}
