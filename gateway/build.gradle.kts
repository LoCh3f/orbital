plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass = "com.orbital.gateway.AppKt"
}

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
}

dependencies {
    implementation(project(":libs:core"))
    implementation(project(":libs:models"))

    // Specific for gateway
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.rate.limit)

    // Redis client (Jedis)
    implementation(libs.jedis)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.ktor.test)
}
