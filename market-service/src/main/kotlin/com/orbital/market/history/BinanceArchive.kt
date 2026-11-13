@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.orbital.market.history

import com.orbital.models.HistoryValue
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Explicit coin IDs prevent symbol collisions (for example wrapped assets). Only active IDs import.
 */
object BinancePairs {
  val known =
      mapOf(
          "bitcoin" to "BTCUSDT",
          "ethereum" to "ETHUSDT",
          "binancecoin" to "BNBUSDT",
          "ripple" to "XRPUSDT",
          "solana" to "SOLUSDT",
          "dogecoin" to "DOGEUSDT",
          "cardano" to "ADAUSDT",
          "tron" to "TRXUSDT",
          "chainlink" to "LINKUSDT",
          "avalanche-2" to "AVAXUSDT",
          "stellar" to "XLMUSDT",
          "polkadot" to "DOTUSDT",
          "litecoin" to "LTCUSDT",
          "bitcoin-cash" to "BCHUSDT",
          "usd-coin" to "USDCUSDT",
          "the-open-network" to "TONUSDT",
          "shiba-inu" to "SHIBUSDT")
}

data class ArchiveResponse(val status: Int, val body: ByteArray)

fun interface ArchiveTransport {
  fun get(url: String, maximumBytes: Int): ArchiveResponse
}

/** No redirects, credentials, user-provided hosts, or unbounded downloads. */
class PublicArchiveTransport : ArchiveTransport {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

  override fun get(url: String, maximumBytes: Int): ArchiveResponse {
    val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    val bytes = response.body().use { it.readNBytes(maximumBytes + 1) }
    require(bytes.size <= maximumBytes) { "Archive response exceeds size limit" }
    return ArchiveResponse(response.statusCode(), bytes)
  }
}

class BinanceArchive(private val transport: ArchiveTransport = PublicArchiveTransport()) {
  private val listings = mutableMapOf<String, Pair<Long, List<String>>>()

  fun files(pair: String, interval: String, from: Long, to: Long): List<String> {
    require(pair.matches(Regex("[A-Z0-9]{4,32}")))
    require(interval in setOf("1d", "1h", "5m"))
    val base = "$pair-$interval-"
    val monthlyPrefix = "data/spot/monthly/klines/$pair/$interval/"
    val monthly =
        list(monthlyPrefix)
            .filter { it.endsWith(".zip") }
            .associateBy {
              YearMonth.parse(it.substringAfterLast('/').removePrefix(base).removeSuffix(".zip"))
            }
    val dailyPrefix = "data/spot/daily/klines/$pair/$interval/"
    val requestedStart = month(from)
    val requestedEnd = minOf(month(to), YearMonth.now(ZoneOffset.UTC))
    val earliest =
        monthly.keys.minOrNull()
            ?: list(dailyPrefix)
                .filter { it.endsWith(".zip") }
                .minOfOrNull {
                  YearMonth.from(
                      LocalDate.parse(
                          it.substringAfterLast('/').removePrefix(base).removeSuffix(".zip")))
                }
            ?: return emptyList()
    var cursor = maxOf(requestedStart, earliest)
    val selected = mutableListOf<String>()
    while (cursor <= requestedEnd) {
      val monthKey = monthly[cursor]
      if (monthKey != null) selected += monthKey
      else
          selected +=
              list(dailyPrefix + base + cursor).filter { key ->
                if (!key.endsWith(".zip")) false
                else {
                  val date =
                      LocalDate.parse(
                          key.substringAfterLast('/').removePrefix(base).removeSuffix(".zip"))
                  val start = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                  start <= to && start + 86400 > from
                }
              }
      cursor = cursor.plusMonths(1)
    }
    // Recent data appears first while the durable job progressively adds older months.
    return selected.distinct().sortedByDescending { it.substringAfterLast('/').removePrefix(base) }
  }

  private fun month(timestamp: Long): YearMonth =
      YearMonth.from(Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC))

  private fun list(prefix: String): List<String> {
    val now = Instant.now().epochSecond
    listings[prefix]
        ?.takeIf { now - it.first < 21600 }
        ?.let {
          return it.second
        }
    val keys = mutableListOf<String>()
    var token: String? = null
    repeat(100) {
      val url =
          "https://s3-ap-northeast-1.amazonaws.com/data.binance.vision?list-type=2&prefix=" +
              URLEncoder.encode(prefix, "UTF-8") +
              (token?.let { "&continuation-token=" + URLEncoder.encode(it, "UTF-8") } ?: "")
      val response = transport.get(url, 2_000_000)
      check(response.status == 200) { "Binance listing unavailable (${response.status})" }
      val factory =
          DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
          }
      val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(response.body))
      val nodes = document.getElementsByTagName("Key")
      for (index in 0 until nodes.length) {
        val key = nodes.item(index).textContent
        require(key.startsWith(prefix) && !key.contains("..")) { "Unexpected archive key" }
        keys += key
      }
      val next = document.getElementsByTagName("NextContinuationToken").item(0)?.textContent
      if (document.getElementsByTagName("IsTruncated").item(0)?.textContent != "true") {
        listings[prefix] = now to keys
        return keys
      }
      require(!next.isNullOrBlank() && next != token) { "Invalid archive pagination" }
      token = next
    }
    error("Archive listing exceeded page limit")
  }

  fun download(key: String): Pair<String, List<HistoryValue>> {
    require(
        key.matches(
            Regex(
                "data/spot/(monthly|daily)/klines/[A-Z0-9]+/(1d|1h|5m)/[A-Z0-9]+-(1d|1h|5m)-[0-9-]+\\.zip")))
    val checksumResponse = transport.get("https://data.binance.vision/$key.CHECKSUM", 1024)
    check(checksumResponse.status == 200) { "Archive checksum unavailable" }
    val expected = checksumResponse.body.decodeToString().trim().split(Regex("\\s+")).first()
    require(expected.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid archive checksum" }
    val response = transport.get("https://data.binance.vision/$key", 8_000_000)
    check(response.status == 200) { "Archive unavailable (${response.status})" }
    val actual =
        MessageDigest.getInstance("SHA-256").digest(response.body).joinToString("") {
          "%02x".format(it)
        }
    require(actual.equals(expected, true)) { "Archive checksum mismatch" }
    return actual to parseZip(response.body)
  }

  companion object {
    fun parseZip(bytes: ByteArray): List<HistoryValue> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
          val entry = zip.nextEntry ?: error("Empty archive")
          require(entry.name.endsWith(".csv") && !entry.isDirectory) {
            "Unexpected archive content"
          }
          val csv = zip.readNBytes(64_000_001)
          require(csv.size <= 64_000_000) { "Expanded archive exceeds size limit" }
          parseCsv(csv.decodeToString())
        }

    fun parseCsv(csv: String): List<HistoryValue> =
        csv.lineSequence()
            .filter { it.isNotBlank() && it.first().isDigit() }
            .map { line ->
              val fields = line.trim().split(',')
              require(fields.size >= 7) { "Malformed kline" }
              val rawTime = fields[6].toLong()
              // Binance SPOT switched from milliseconds to microseconds in January 2025.
              val timestamp = rawTime / if (rawTime >= 100_000_000_000_000L) 1_000_000L else 1000L
              val price = fields[4].toDouble()
              require(timestamp > 0 && price.isFinite() && price > 0) { "Invalid kline value" }
              HistoryValue(timestamp, price)
            }
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
            .toList()
  }
}
