plugins {
    kotlin("jvm") version "1.9.25"
    jacoco
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit.jupiter)
}

tasks.test { useJUnitPlatform() }

jacoco { toolVersion = "0.8.11" }
