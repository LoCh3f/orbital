package com.orbital.market.plugins

import com.orbital.market.api.CoinGeckoClient
import com.orbital.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

private val TEST_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val VALID_COIN_JSON =
    """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"https://example.com/btc.png",
    "current_price":65000.0,"market_cap":1000000,"market_cap_rank":1,"total_volume":500000,
    "price_change_percentage_24h":2.35,"last_updated":"2024-01-01T00:00:00Z"}]"""

private fun coinGeckoClient(responseBody: () -> String) =
    CoinGeckoClient(
        HttpClient(
            MockEngine { _ ->
              respond(
                  content = responseBody(),
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
              install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            })

class RoutingTest {
  @Test
  fun `health route returns healthy status`() = testApplication {
    application {
      configureSerialization()
      configureRouting(TEST_SCOPE, coinGeckoClient { VALID_COIN_JSON })
    }
    val response = client.get("/health")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("healthy"))
  }

  @Test
  fun `prices route returns mapped coin prices`() = testApplication {
    application {
      configureSerialization()
      configureRouting(TEST_SCOPE, coinGeckoClient { VALID_COIN_JSON })
    }
    val response = client.get("/api/v1/market/prices")
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("\"coinId\""))
    assertTrue(body.contains("\"BTC\""))
  }

  @Test
  fun `single coin not found returns 404`() = testApplication {
    application {
      configureSerialization()
      configureRouting(TEST_SCOPE, coinGeckoClient { "[]" })
    }
    val response = client.get("/api/v1/market/prices/doesnotexist")
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `malformed upstream payload surfaces as server error`() = testApplication {
    application {
      configureSerialization()
      configureRouting(TEST_SCOPE, coinGeckoClient { "not-json" })
    }
    val response = client.get("/api/v1/market/prices")
    assertEquals(HttpStatusCode.InternalServerError, response.status)
  }
}
