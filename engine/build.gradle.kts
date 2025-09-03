plugins {
    kotlin("jvm") version "1.9.25"
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// ----- perfTest source set -----
sourceSets {
    val perfTest by creating {
        compileClasspath += sourceSets["main"].output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    add("perfTestImplementation", libs.junit.jupiter)
}

val perfTest by tasks.registering(Test::class) {
    description = "Runs JUnit performance/load tests"
    group = "verification"
    testClassesDirs = sourceSets["perfTest"].output.classesDirs
    classpath = sourceSets["perfTest"].runtimeClasspath
    useJUnitPlatform()
}

// ----- JMH -----
jmh {
    jvmArgsAppend = listOf("-Djmh.separateClasspathJAR=true")
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)

    // Fast defaults
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    timeOnIteration.set("1s")

    if (project.hasProperty("prodJmh")) {
        warmupIterations.set(10)
        iterations.set(10)
        fork.set(2)
        timeOnIteration.set("10s")
    }
}