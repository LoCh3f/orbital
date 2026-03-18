plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}
application {
    mainClass = "com.orbital.market.MarketApplicationKt"
}
dependencies {
    implementation(project(":libs:core"))
    implementation(project(":libs:models"))
}