plugins {
    alias(libs.plugins.jvm)
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)

    // (Swagger UI placeholder; we’ll wire docs later)
    implementation(libs.ktor.server.swagger)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test) 
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.valr.app.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        // Always show standard out/err
        showStandardStreams = true

        // Events to log
        events("passed", "skipped", "failed")

        // Show full exception stack traces
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("app")
    archiveClassifier.set("all")
    archiveVersion.set("")
}
