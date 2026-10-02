import org.jetbrains.compose.ExperimentalComposeLibrary
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

val orbitalDemoBuild = (project.findProperty("orbitalDemoBuild") as String?)?.toBoolean() ?: false

val generateDemoBuildConfig by
    tasks.registering {
      val outputDir = layout.buildDirectory.dir("generated/demoBuildConfig/wasmJsMain/kotlin")
      inputs.property("orbitalDemoBuild", orbitalDemoBuild)
      outputs.dir(outputDir)
      doLast {
        val file = outputDir.get().file("io/orbital/app/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.orbital.app
            |
            |internal const val IS_DEMO_BUILD: Boolean = $orbitalDemoBuild
            |"""
                .trimMargin())
      }
    }

kotlin {
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("orbitalApp")
        browser {
            commonWebpackConfig { outputFileName = "orbitalApp.js" }
            testTask {
                useKarma { useChromeHeadless() }
            }
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
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test.orbital)
                implementation(libs.ktor.client.mock.orbital)
                implementation(libs.ktor.client.content.negotiation.orbital)
                implementation(libs.ktor.serialization.kotlinx.json.orbital)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio.orbital)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                @OptIn(ExperimentalComposeLibrary::class) implementation(compose.uiTest)
            }
        }
        val wasmJsMain by getting {
            kotlin.srcDir(generateDemoBuildConfig)
            dependencies { implementation(libs.ktor.client.js.orbital) }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.orbital.app.MainKt"
    }
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/desktopMain/kotlin",
        "src/wasmJsMain/kotlin",
    )
}
