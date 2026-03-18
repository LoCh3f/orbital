plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass = "com.orbital.gateway.AppKt"
}

dependencies {
    implementation(project(":libs:core"))
    implementation(project(":libs:models"))

    // Specific for gateway
    implementation("io.ktor:ktor-server-netty-jvm:2.3.5")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.5")
}