import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// compose-webview-multiplatform (desktop KCEF backend) is published here.
repositories { maven("https://jogamp.org/deployment/maven") }

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
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(compose.components.resources)
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
                implementation("io.github.kevinnzou:compose-webview-multiplatform:2.0.3")
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
        // Required by KCEF (embedded Chromium, used to render price charts) on the JVM.
        jvmArgs += listOf(
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED")
    }
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/desktopMain/kotlin",
        "src/wasmJsMain/kotlin",
    )
}
