plugins {
    kotlin("jvm") version "1.9.25"
    jacoco
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
}

jacoco {
    toolVersion = "0.8.11"
}
