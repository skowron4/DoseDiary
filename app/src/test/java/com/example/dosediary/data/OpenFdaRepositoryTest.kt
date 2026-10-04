package com.example.dosediary.data

import com.example.dosediary.data.remote.HttpClientFactory
import com.example.dosediary.data.remote.OpenFdaApi
import com.example.dosediary.data.repository.DrugSearchRepositoryImpl
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class OpenFdaRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DrugSearchRepositoryImpl {
        val client = HttpClientFactory.create(MockEngine(handler), enableLogging = false)
        return DrugSearchRepositoryImpl(OpenFdaApi(client), Dispatchers.Unconfined)
    }

    @Test
    fun `parses labels, tolerates missing fields and drops unusable rows`() = runTest {
        val body = """
            {
              "meta": {"results": {"skip": 0, "limit": 10, "total": 3}},
              "results": [
                {
                  "id": "abc-1",
                  "unknown_field": 42,
                  "purpose": ["Purpose Pain reliever/fever reducer"],
                  "openfda": {
                    "brand_name": ["ADVIL"],
                    "generic_name": ["IBUPROFEN"],
                    "manufacturer_name": ["Haleon US Holdings LLC"],
                    "route": ["ORAL"]
                  }
                },
                { "id": "abc-2", "openfda": { "brand_name": ["Advil PM"] } },
                { "id": "abc-3", "openfda": {} },
                { "openfda": { "brand_name": ["No id"] } }
              ]
            }
        """.trimIndent()

        val result = repository { respond(body, HttpStatusCode.OK, jsonHeaders) }.searchByBrandName("advil")

        val medications = (result as AppResult.Success).data
        assertEquals(listOf("abc-1", "abc-2"), medications.map { it.id })
        with(medications.first()) {
            assertEquals("Advil", commercialName)
            assertEquals("Ibuprofen", activeSubstance)
            assertEquals("Haleon US Holdings LLC", manufacturer)
            assertEquals("Pain reliever/fever reducer", purpose)
            assertEquals("Oral", route)
        }
        assertEquals(null, medications[1].activeSubstance)
    }

    @Test
    fun `active substance prefers substance_name and falls back to generic_name`() = runTest {
        val body = """
            {"results": [
              {"id": "a", "openfda": {"brand_name": ["A"], "substance_name": ["IBUPROFEN"], "generic_name": ["Pain pills"]}},
              {"id": "b", "openfda": {"brand_name": ["B"], "substance_name": [], "generic_name": ["ACETAMINOPHEN"]}}
            ]}
        """.trimIndent()

        val result = repository { respond(body, HttpStatusCode.OK, jsonHeaders) }.searchByBrandName("x1")

        val medications = (result as AppResult.Success).data
        assertEquals(listOf("Ibuprofen", "Acetaminophen"), medications.map { it.activeSubstance })
    }

    @Test
    fun `request targets the label endpoint with a quoted brand-name search and limit`() = runTest {
        var captured: HttpRequestData? = null
        repository {
            captured = it
            respond("""{"results": []}""", HttpStatusCode.OK, jsonHeaders)
        }.searchByBrandName("Advil PM")

        val url = captured!!.url
        assertEquals("api.fda.gov", url.host)
        assertEquals("/drug/label.json", url.encodedPath)
        assertEquals("openfda.brand_name:\"Advil PM\"", url.parameters["search"])
        assertEquals("10", url.parameters["limit"])
    }

    @Test
    fun `special characters are stripped from the query`() = runTest {
        var captured: HttpRequestData? = null
        repository {
            captured = it
            respond("""{"results": []}""", HttpStatusCode.OK, jsonHeaders)
        }.searchByBrandName("ad\"vil*:(x)")

        assertEquals("openfda.brand_name:\"ad vil x\"", captured!!.url.parameters["search"])
    }

    @Test
    fun `404 NOT_FOUND from OpenFDA means no results, not an error`() = runTest {
        val body = """{"error": {"code": "NOT_FOUND", "message": "No matches found!"}}"""
        val result = repository { respond(body, HttpStatusCode.NotFound, jsonHeaders) }.searchByBrandName("zzzzzz")

        assertEquals(AppResult.Success(emptyList<Nothing>()), result)
    }

    @Test
    fun `5xx maps to a server error carrying the status code`() = runTest {
        val result = repository {
            respond("oops", HttpStatusCode.InternalServerError, jsonHeaders)
        }.searchByBrandName("advil")

        assertEquals(AppResult.Failure(DomainError.Server(500)), result)
    }

    @Test
    fun `429 rate limiting maps to a server error`() = runTest {
        val result = repository {
            respond("", HttpStatusCode.TooManyRequests, jsonHeaders)
        }.searchByBrandName("advil")

        assertEquals(AppResult.Failure(DomainError.Server(429)), result)
    }

    @Test
    fun `unreachable host maps to no internet`() = runTest {
        val result = repository { throw UnknownHostException("api.fda.gov") }.searchByBrandName("advil")
        assertEquals(AppResult.Failure(DomainError.NoInternet), result)
    }

    @Test
    fun `socket timeout maps to timeout`() = runTest {
        val result = repository { throw SocketTimeoutException("slow") }.searchByBrandName("advil")
        assertEquals(AppResult.Failure(DomainError.Timeout), result)
    }

    @Test
    fun `malformed json maps to a parsing error`() = runTest {
        val result = repository {
            respond("""{"results": "not-a-list"}""", HttpStatusCode.OK, jsonHeaders)
        }.searchByBrandName("advil")

        assertTrue(result is AppResult.Failure)
        assertEquals(DomainError.Parsing, (result as AppResult.Failure).error)
    }
}
