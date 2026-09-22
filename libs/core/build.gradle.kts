plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    // ktor
    api(libs.ktor.server.core)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json.jvm)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.call.logging)
    api(libs.ktor.server.call.id)
    api(libs.logback.classic)
    api(libs.ktor.server.netty)
    // Monitoring base
    api(libs.ktor.server.metrics.micrometer)
    api(libs.micrometer.registry.prometheus)

    // Structured (JSON) logging
    api(libs.logstash.logback.encoder)

    // Models library
    api(project(":libs:models"))

    // Ktor Client
    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.client.logging)

    // Kotlin Serialization
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
