plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
}
allprojects {
    group = "com.orbital"
    version = "1.0.2" // x-release-please-version

    repositories {
        mavenCentral()
        google()
    }
}


subprojects {
    // orbital-app is a true Kotlin Multiplatform module (kotlin("multiplatform")),
    // which cannot coexist with kotlin("jvm") being force-applied here.
    // "libs" is a synthetic path container for libs:core/libs:models (no source of its
    // own) — applying Dokka to it registers a second multi-module aggregator that
    // conflicts with the root project's own dokkaHtmlMultiModule task.
    if (name == "orbital-app" || name == "libs") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "com.ncorti.ktfmt.gradle")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.jetbrains.dokka")

    plugins.withType<org.gradle.api.plugins.JavaPlugin> {
        tasks.named("ktfmtCheckMain") {
            dependsOn(tasks.named("ktfmtFormatMain"))
        }
        tasks.named("detekt") {
            dependsOn(tasks.named("ktfmtFormatMain"))
        }
    }
}
