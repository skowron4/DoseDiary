package com.example.dosediary.di

import android.content.Context
import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Fails the build if any class in the graph (ViewModels, use cases, repositories, ...) has a
 * constructor dependency that no Koin module provides, instead of crashing at app launch.
 */
class KoinGraphTest {

    @Test
    fun `every dependency in the Koin graph can be resolved`() {
        val allModules = module { includes(appModule, networkModule, databaseModule) }

        allModules.verify(
            extraTypes = listOf(
                Context::class, // provided at runtime by androidContext()
                CoroutineDispatcher::class, // passed explicitly (Dispatchers.IO) in appModule
                String::class, // AddSymptomViewModel params, supplied via parametersOf()
                Long::class,
                // Ktor's own HttpClient constructor parameter; the client is built via HttpClientFactory.
                HttpClientConfig::class,
            ),
        )
    }
}
