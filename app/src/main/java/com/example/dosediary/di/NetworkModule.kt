package com.example.dosediary.di

import com.example.dosediary.BuildConfig
import com.example.dosediary.data.remote.HttpClientFactory
import com.example.dosediary.data.remote.OpenFdaApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module

val networkModule = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<HttpClient> {
        HttpClientFactory.create(engine = get(), enableLogging = BuildConfig.DEBUG)
    }
    single { OpenFdaApi(client = get()) }
}
