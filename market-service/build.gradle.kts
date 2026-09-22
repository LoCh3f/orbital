plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass = "com.orbital.market.MarketAppKt"
}

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
}
dependencies {
    implementation(project(":libs:core"))
    implementation(project(":libs:models"))
    implementation(libs.kotlinx.coroutines.core)

    // Persistence: Exposed + HikariCP + Postgres driver
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.ktor.test)
}
