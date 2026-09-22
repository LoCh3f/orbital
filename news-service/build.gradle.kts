plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass = "com.orbital.news.NewsAppKt"
}

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
}

dependencies {
    implementation(project(":libs:core"))
    implementation(project(":libs:models"))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json.jvm)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Persistence: Exposed + HikariCP + Postgres driver
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.jedis)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.ktor.test)
}
