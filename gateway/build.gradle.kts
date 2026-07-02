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

// Ensure ktfmt format runs before ktfmt check to avoid Gradle work validation errors
tasks.matching { it.name.startsWith("ktfmtCheck") }.configureEach {
    dependsOn(tasks.matching { t -> t.name.startsWith("ktfmtFormat") })
}

// Ensure ktfmt formatting runs before detekt to avoid work validation issues
tasks.matching { it.name.startsWith("detekt") }.configureEach {
    dependsOn(tasks.matching { t -> t.name.startsWith("ktfmtFormat") })
}