plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("io.ktor:ktor-server-netty-jvm:2.3.5")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.5")

    // Redis client (Jedis)
    implementation("redis.clients:jedis:4.3.1")
}

