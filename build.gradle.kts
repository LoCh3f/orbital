plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    id("com.ncorti.ktfmt.gradle") version "0.17.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.4" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
}
allprojects {
    group = "com.orbital"
    version = "1.0.0"

    repositories {
        mavenCentral()
        google()
    }
}


subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "com.ncorti.ktfmt.gradle")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.diffplug.spotless")
}