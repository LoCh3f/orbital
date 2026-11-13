package com.orbital.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** Shared health endpoint. Service name can be provided by each module. */
fun Application.configureMonitoring(serviceName: String = "orbital") {
  routing { get("/health") { call.respond(mapOf("status" to "OK", "service" to serviceName)) } }
}

/**
 * Installs a Prometheus-backed Micrometer registry, binds standard JVM/process metrics, and exposes
 * them at `/metrics`. Every metric is tagged with `application=<serviceName>` so a single Grafana
 * dashboard can filter/compare across services.
 */
fun Application.configureMetrics(serviceName: String): PrometheusMeterRegistry {
  val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
  registry.config().commonTags("application", serviceName)

  listOf(
          ClassLoaderMetrics(),
          JvmMemoryMetrics(),
          JvmGcMetrics(),
          ProcessorMetrics(),
          JvmThreadMetrics(),
          UptimeMetrics())
      .forEach { it.bindTo(registry) }

  install(MicrometerMetrics) { this.registry = registry }
  routing { get("/metrics") { call.respond(registry.scrape()) } }

  return registry
}

/** Liveness stays independent of dependencies; readiness checks required dependencies. */
fun Application.configureReadiness(check: suspend () -> Boolean) {
  routing {
    get("/ready") {
      val ready =
          try {
            check()
          } catch (_: Exception) {
            false
          }
      call.respond(
          if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
          mapOf("status" to if (ready) "ready" else "unavailable"))
    }
  }
}
