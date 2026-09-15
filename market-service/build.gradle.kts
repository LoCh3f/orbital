plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Persistence: Exposed + HikariCP + Postgres driver
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.6.0")

    testImplementation(kotlin("test"))
}