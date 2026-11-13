package com.orbital.market.history

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BinanceArchiveTest {
  @Test
  fun `millisecond and microsecond candles use closing timestamps and prices`() {
    val milliseconds = "1700000000000,1,3,1,2.5,10,1700000299999,0,0,0,0,0"
    val microseconds = "1735689600000000,1,3,1,2.5,10,1735689899999999,0,0,0,0,0"
    assertEquals(1700000299L, BinanceArchive.parseCsv(milliseconds).single().timestamp)
    assertEquals(1735689899L, BinanceArchive.parseCsv(microseconds).single().timestamp)
    assertEquals(2.5, BinanceArchive.parseCsv(milliseconds).single().price)
    assertFailsWith<IllegalArgumentException> {
      BinanceArchive.parseCsv("1700000000,1,1,1,NaN,1,1700000300000")
    }
  }

  @Test
  fun `checksum failure prevents importing an archive`() {
    val archive =
        BinanceArchive(
            ArchiveTransport { url, _ ->
              ArchiveResponse(
                  200,
                  if (url.endsWith(".CHECKSUM")) "${"0".repeat(64)} file.zip".toByteArray()
                  else byteArrayOf(1, 2, 3))
            })
    assertFailsWith<IllegalArgumentException> {
      archive.download("data/spot/monthly/klines/BTCUSDT/1d/BTCUSDT-1d-2017-08.zip")
    }
    assertFailsWith<IllegalArgumentException> { archive.download("../../etc/passwd") }
  }

  @Test
  fun `zip parser reads CSV without extracting filesystem paths`() {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      zip.putNextEntry(ZipEntry("../../fixture.csv"))
      zip.write("1700000000000,1,3,1,2.5,10,1700000299999,0,0,0,0,0".toByteArray())
      zip.closeEntry()
    }
    assertEquals(2.5, BinanceArchive.parseZip(bytes.toByteArray()).single().price)
  }

  @Test
  fun `listing pagination uses monthly files and fills only missing months from daily files`() {
    var requests = 0
    val archive =
        BinanceArchive(
            ArchiveTransport { url, _ ->
              requests++
              val query = URLDecoder.decode(URI(url).rawQuery, "UTF-8")
              val monthly = "data/spot/monthly/klines/BTCUSDT/1d/BTCUSDT-1d-2024-01.zip"
              val daily = "data/spot/daily/klines/BTCUSDT/1d/BTCUSDT-1d-2024-02-01.zip"
              val xml =
                  when {
                    "continuation-token=next" in query ->
                        "<ListBucketResult><IsTruncated>false</IsTruncated>" +
                            "<Contents><Key>$monthly.CHECKSUM</Key></Contents></ListBucketResult>"
                    "monthly" in query ->
                        "<ListBucketResult><IsTruncated>true</IsTruncated>" +
                            "<NextContinuationToken>next</NextContinuationToken>" +
                            "<Contents><Key>$monthly</Key></Contents></ListBucketResult>"
                    else ->
                        "<ListBucketResult><IsTruncated>false</IsTruncated>" +
                            "<Contents><Key>$daily</Key></Contents></ListBucketResult>"
                  }
              ArchiveResponse(200, xml.toByteArray())
            })
    val from = Instant.parse("2024-01-01T00:00:00Z").epochSecond
    val to = Instant.parse("2024-02-02T00:00:00Z").epochSecond
    val files = archive.files("BTCUSDT", "1d", from, to)
    assertEquals(2, files.size)
    assertTrue(files.first().contains("daily"))
    archive.files("BTCUSDT", "1d", from, to)
    assertEquals(3, requests)
  }

  @Test
  fun `valid checksum permits a bounded archive import`() {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      zip.putNextEntry(ZipEntry("fixture.csv"))
      zip.write("1700000000000,1,3,1,2.5,10,1700000299999,0,0,0,0,0".toByteArray())
      zip.closeEntry()
    }
    val content = bytes.toByteArray()
    val hash =
        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    val archive =
        BinanceArchive(
            ArchiveTransport { url, _ ->
              ArchiveResponse(
                  200,
                  if (url.endsWith(".CHECKSUM")) "$hash fixture.zip".toByteArray() else content)
            })
    val (checksum, points) =
        archive.download("data/spot/monthly/klines/BTCUSDT/1d/BTCUSDT-1d-2017-08.zip")
    assertEquals(hash, checksum)
    assertEquals(2.5, points.single().price)
  }

  @Test
  fun `real archive fixture has verified prices and listing start when supplied`() {
    val path = System.getenv("TEST_BINANCE_ARCHIVE") ?: return
    val content = java.io.File(path).readBytes()
    val checksum = java.io.File("$path.CHECKSUM").readText().substringBefore(' ')
    val hash =
        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    assertEquals(checksum, hash)
    val points = BinanceArchive.parseZip(content)
    assertEquals(15, points.size)
    assertEquals(Instant.parse("2017-08-17T23:59:59Z").epochSecond, points.first().timestamp)
    assertTrue(points.all { it.price > 0 })
  }
}
