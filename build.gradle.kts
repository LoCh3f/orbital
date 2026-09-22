plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka) apply false
}
allprojects {
    group = "com.orbital"
    version = "1.0.0" // x-release-please-version

    repositories {
        mavenCentral()
        google()
    }
}


subprojects {
    // orbital-app is a true Kotlin Multiplatform module (kotlin("multiplatform")),
    // which cannot coexist with kotlin("jvm") being force-applied here.
    if (name == "orbital-app") return@subprojects

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
