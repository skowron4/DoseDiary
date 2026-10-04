package com.example.dosediary.domain.util

/** Abstraction over the system clock so time-dependent use cases stay testable and platform-free. */
fun interface Clock {
    fun nowMillis(): Long
}
