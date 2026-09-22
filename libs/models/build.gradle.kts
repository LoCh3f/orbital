plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))

    // models should be standalone data types; avoid depending on core to prevent circular dependencies
}
