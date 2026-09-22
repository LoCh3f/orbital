import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

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
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material)
                implementation(libs.compose.ui)
                implementation(libs.ktor.client.core.orbital)
                implementation(libs.ktor.client.content.negotiation.orbital)
                implementation(libs.ktor.serialization.kotlinx.json.orbital)
                implementation(libs.kotlinx.serialization.json.orbital)
                implementation(libs.kotlinx.coroutines.core.orbital)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio.orbital)
            }
        }
        val wasmJsMain by getting {
            dependencies { implementation(libs.ktor.client.js.orbital) }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.orbital.app.MainKt"
    }
}
