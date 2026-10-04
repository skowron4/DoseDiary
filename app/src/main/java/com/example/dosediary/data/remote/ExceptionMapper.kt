package com.example.dosediary.data.remote

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Runs a network call and converts every failure into a typed [DomainError].
 * [CancellationException] is always rethrown so structured concurrency keeps working.
 */
internal suspend inline fun <T> safeNetworkCall(crossinline block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(e.toDomainError())
    }

internal fun Throwable.toDomainError(): DomainError = when (this) {
    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException,
    is java.net.SocketTimeoutException -> DomainError.Timeout

    is ResponseException -> DomainError.Server(response.status.value)
    // Ktor wraps deserialization failures in ContentConvertException (JsonConvertException);
    // NoTransformationFoundException means the body type didn't match what we can decode.
    is ContentConvertException,
    is NoTransformationFoundException,
    is SerializationException -> DomainError.Parsing
    // UnknownHostException, ConnectException, SSL errors... all extend IOException.
    is IOException -> DomainError.NoInternet
    else -> DomainError.Unknown
}
