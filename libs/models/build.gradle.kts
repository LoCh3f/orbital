plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // models should be standalone data types; avoid depending on core to prevent circular dependencies
}
