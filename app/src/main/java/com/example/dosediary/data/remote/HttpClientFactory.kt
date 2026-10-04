package com.example.dosediary.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Builds the shared client. The [engine] is injectable so tests can pass a `MockEngine`. */
    fun create(engine: HttpClientEngine, enableLogging: Boolean): HttpClient = HttpClient(engine) {
        // Throw ClientRequestException / ServerResponseException for non-2xx responses.
        expectSuccess = true

        install(ContentNegotiation) { json(json) }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
        }

        if (enableLogging) {
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.INFO
            }
        }

        defaultRequest {
            accept(ContentType.Application.Json)
        }
    }
}
