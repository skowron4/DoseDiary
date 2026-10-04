package com.example.dosediary.domain.model

/** Errors the domain layer can surface. Presentation maps these to user-facing text. */
sealed interface DomainError {
    /** The device has no connectivity or the host could not be resolved. */
    data object NoInternet : DomainError

    /** The request took too long. */
    data object Timeout : DomainError

    /** The server answered with an unexpected status code. */
    data class Server(val code: Int) : DomainError

    /** The response could not be parsed. */
    data object Parsing : DomainError

    /** Input rejected by business rules. */
    data class Validation(val reason: ValidationReason) : DomainError

    /** A local storage operation failed. */
    data object Storage : DomainError

    data object Unknown : DomainError
}

enum class ValidationReason {
    QUERY_TOO_SHORT,
    SEVERITY_OUT_OF_RANGE,
    NOTES_TOO_LONG,
    NICKNAME_TOO_LONG,
    MEDICATION_NOT_FOUND,
}

/** Minimal Result type carrying a typed [DomainError] (kotlin.Result only supports Throwable). */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Failure(val error: DomainError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (DomainError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
