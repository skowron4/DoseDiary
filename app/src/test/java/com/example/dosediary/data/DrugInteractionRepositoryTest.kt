package com.example.dosediary.data

import com.example.dosediary.data.remote.HttpClientFactory
import com.example.dosediary.data.remote.OpenFdaApi
import com.example.dosediary.data.repository.DrugInteractionRepositoryImpl
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Medication
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DrugInteractionRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val advil = Medication(id = "abc-1", commercialName = "Advil", activeSubstance = "Ibuprofen")

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DrugInteractionRepositoryImpl {
        val client = HttpClientFactory.create(MockEngine(handler), enableLogging = false)
        return DrugInteractionRepositoryImpl(OpenFdaApi(client), Dispatchers.Unconfined)
    }

    private fun labelWith(interactions: String) = """{"results": [{"id": "abc-1", "drug_interactions": $interactions}]}"""

    @Test
    fun `joins the interaction paragraphs and drops the section heading`() = runTest {
        val body = labelWith(
            """["7 DRUG INTERACTIONS Warfarin:   increased\nbleeding risk.", "Aspirin may reduce effect."]""",
        )

        val result = repository { respond(body, HttpStatusCode.OK, jsonHeaders) }.getInteractionText(advil)

        assertEquals(
            AppResult.Success<String?>("Warfarin: increased bleeding risk. Aspirin may reduce effect."),
            result,
        )
    }

    @Test
    fun `a label without an interaction section yields null, not an error`() = runTest {
        val body = """{"results": [{"id": "abc-1", "openfda": {"brand_name": ["ADVIL"]}}]}"""
        val result = repository { respond(body, HttpStatusCode.OK, jsonHeaders) }.getInteractionText(advil)
        assertEquals(AppResult.Success<String?>(null), result)
    }

    @Test
    fun `blank or empty interaction text yields null`() = runTest {
        assertEquals(
            AppResult.Success<String?>(null),
            repository { respond(labelWith("""["  ", ""]"""), HttpStatusCode.OK, jsonHeaders) }.getInteractionText(advil),
        )
        assertEquals(
            AppResult.Success<String?>(null),
            repository { respond(labelWith("[]"), HttpStatusCode.OK, jsonHeaders) }.getInteractionText(advil),
        )
    }

    @Test
    fun `empty results yield null`() = runTest {
        val result = repository { respond("""{"results": []}""", HttpStatusCode.OK, jsonHeaders) }.getInteractionText(advil)
        assertEquals(AppResult.Success<String?>(null), result)
    }

    @Test
    fun `404 NOT_FOUND means the label is gone, not an error`() = runTest {
        val body = """{"error": {"code": "NOT_FOUND", "message": "No matches found!"}}"""
        val result = repository { respond(body, HttpStatusCode.NotFound, jsonHeaders) }.getInteractionText(advil)
        assertEquals(AppResult.Success<String?>(null), result)
    }

    @Test
    fun `request looks the label up by id with a limit of one`() = runTest {
        var captured: HttpRequestData? = null
        repository {
            captured = it
            respond("""{"results": []}""", HttpStatusCode.OK, jsonHeaders)
        }.getInteractionText(advil)

        val url = captured!!.url
        assertEquals("api.fda.gov", url.host)
        assertEquals("/drug/label.json", url.encodedPath)
        assertEquals("id:\"abc-1\" OR set_id:\"abc-1\"", url.parameters["search"])
        assertEquals("1", url.parameters["limit"])
    }

    @Test
    fun `characters that could alter the search are stripped from the id`() = runTest {
        var captured: HttpRequestData? = null
        repository {
            captured = it
            respond("""{"results": []}""", HttpStatusCode.OK, jsonHeaders)
        }.getInteractionText(advil.copy(id = "ab\" OR id:*c"))

        assertEquals("id:\"abORidc\" OR set_id:\"abORidc\"", captured!!.url.parameters["search"])
    }

    @Test
    fun `an id with no usable characters makes no request`() = runTest {
        var requests = 0
        val result = repository {
            requests++
            respond("""{"results": []}""", HttpStatusCode.OK, jsonHeaders)
        }.getInteractionText(advil.copy(id = "\"*:"))

        assertEquals(AppResult.Success<String?>(null), result)
        assertEquals(0, requests)
    }

    @Test
    fun `5xx maps to a server error`() = runTest {
        val result = repository { respond("oops", HttpStatusCode.InternalServerError, jsonHeaders) }.getInteractionText(advil)
        assertEquals(AppResult.Failure(DomainError.Server(500)), result)
    }

    @Test
    fun `rate limiting maps to a server error`() = runTest {
        val result = repository { respond("", HttpStatusCode.TooManyRequests, jsonHeaders) }.getInteractionText(advil)
        assertEquals(AppResult.Failure(DomainError.Server(429)), result)
    }

    @Test
    fun `offline maps to no internet`() = runTest {
        val result = repository { throw UnknownHostException("api.fda.gov") }.getInteractionText(advil)
        assertEquals(AppResult.Failure(DomainError.NoInternet), result)
    }

    @Test
    fun `socket timeout maps to timeout`() = runTest {
        val result = repository { throw SocketTimeoutException("slow") }.getInteractionText(advil)
        assertEquals(AppResult.Failure(DomainError.Timeout), result)
    }

    @Test
    fun `malformed json maps to a parsing error`() = runTest {
        val result = repository {
            respond("""{"results": [{"id": "abc-1", "drug_interactions": "not-a-list"}]}""", HttpStatusCode.OK, jsonHeaders)
        }.getInteractionText(advil)
        assertEquals(AppResult.Failure(DomainError.Parsing), result)
    }

    @Test
    fun `successful lookups are cached, including labels without interaction text`() = runTest {
        var requests = 0
        val repo = repository {
            requests++
            respond(labelWith("""["Warfarin."]"""), HttpStatusCode.OK, jsonHeaders)
        }

        val first = repo.getInteractionText(advil)
        val second = repo.getInteractionText(advil)

        assertEquals(first, second)
        assertEquals(1, requests)
    }

    @Test
    fun `failures are not cached so a retry can succeed`() = runTest {
        var requests = 0
        val repo = repository {
            requests++
            if (requests == 1) throw UnknownHostException("offline") else respond(labelWith("""["Warfarin."]"""), HttpStatusCode.OK, jsonHeaders)
        }

        assertEquals(AppResult.Failure(DomainError.NoInternet), repo.getInteractionText(advil))
        assertEquals(AppResult.Success<String?>("Warfarin."), repo.getInteractionText(advil))
        assertEquals(2, requests)
    }
}
