plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    jacoco
}

repositories { mavenCentral() }

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:4.5.9"))

    implementation("io.vertx:vertx-core:4.5.9")
    implementation("io.vertx:vertx-web:4.5.9")
    implementation("io.vertx:vertx-web-api-contract:4.5.9")
    implementation("io.vertx:vertx-web-client:4.5.9")
    implementation("io.vertx:vertx-web-openapi:4.5.9")

    // Kotlin language + coroutines
    implementation("io.vertx:vertx-lang-kotlin:4.5.9")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:4.5.9")

    // JSON (Jackson + Kotlin)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // Application layer (DDD)
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":adapters:persistence-inmemory"))

    // Logging
    implementation(libs.logback.classic)

    testImplementation("io.vertx:vertx-junit5")
    testImplementation(kotlin("test"))
    // Ensure JUnit 5 platform + API present
    testImplementation(libs.junit.jupiter)
}


application {
    mainClass.set("com.valr.api.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.shadowJar {
    archiveBaseName.set("api")
    archiveClassifier.set("all")
    mergeServiceFiles()
}

jacoco { toolVersion = "0.8.11" }
