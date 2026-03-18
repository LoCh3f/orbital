plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    // ktor
    api("io.ktor:ktor-server-core-jvm:2.3.5")
    api("io.ktor:ktor-server-content-negotiation-jvm:2.3.5")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.5")
    api("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
    api("io.ktor:ktor-server-call-logging-jvm:2.3.5")
    api("ch.qos.logback:logback-classic:1.5.13")
    api("io.ktor:ktor-server-netty-jvm:2.3.5")
    // Monitoring base
    api("io.ktor:ktor-server-metrics-micrometer-jvm:2.3.5")


    // Ktor Client
    api("io.ktor:ktor-client-core:2.3.5")
    api("io.ktor:ktor-client-cio:2.3.5")
    api("io.ktor:ktor-client-content-negotiation:2.3.5")
    api("io.ktor:ktor-client-logging:2.3.5")

    // Kotlin Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Logging
    api("ch.qos.logback:logback-classic:1.4.11")


}