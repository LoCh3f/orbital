package com.orbital.market.plugins

import com.orbital.market.api.CoinGeckoClient
import com.orbital.market.history.HistoryRepository
import com.orbital.market.persistence.MarketRepository
import com.orbital.models.HistoryValue
import com.orbital.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HistoryRoutingTest {
  @Test
  fun `range route returns real quote and coverage while rejecting invalid or untracked queries`() =
      testApplication {
        MarketRepository.initDatabase(
            "jdbc:h2:mem:history-route-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "")
        HistoryRepository.track(listOf("bitcoin"))
        HistoryRepository.saveArchive(
            "bitcoin",
            "BTCUSDT",
            300,
            "fixture",
            "a".repeat(64),
            listOf(HistoryValue(1700000000, 11.0)))
        application {
          configureSerialization()
          configureRouting(
              CoroutineScope(Dispatchers.Default),
              CoinGeckoClient(HttpClient(MockEngine { error("No upstream request expected") })))
        }
        val response = client.get("/api/v1/market/history-range/bitcoin?range=all")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("USDT", body["quote"]!!.jsonPrimitive.content)
        assertTrue(response.bodyAsText().contains("1700000000"))
        val invalid =
            listOf(
                "bitcoin?source=unknown",
                "bitcoin?range=unknown",
                "bitcoin?from=oops&to=2",
                "bitcoin?from=1",
                "bitcoin?from=2&to=1",
                "bitcoin?from=-1&to=1",
                "ethereum")
        invalid.forEach { query ->
          assertEquals(
              HttpStatusCode.BadRequest,
              client.get("/api/v1/market/history-range/$query").status,
              query)
        }
      }
}
