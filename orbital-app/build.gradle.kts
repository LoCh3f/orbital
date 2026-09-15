plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    application
}

application {
    mainClass = "io.orbital.app.MainKt"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-cio:2.3.5")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}
