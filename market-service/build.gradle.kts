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
}