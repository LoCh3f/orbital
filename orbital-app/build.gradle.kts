import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

val ktorVersion = "3.5.2"
val composeMultiplatformVersion = "1.12.0"

kotlin {
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("orbitalApp")
        browser {
            commonWebpackConfig { outputFileName = "orbitalApp.js" }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:$composeMultiplatformVersion")
                implementation(
                    "org.jetbrains.compose.foundation:foundation:$composeMultiplatformVersion")
                implementation("org.jetbrains.compose.material:material:$composeMultiplatformVersion")
                implementation("org.jetbrains.compose.ui:ui:$composeMultiplatformVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val wasmJsMain by getting {
            dependencies { implementation("io.ktor:ktor-client-js:$ktorVersion") }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.orbital.app.MainKt"
    }
}
