plugins {
    kotlin("jvm") version "1.9.25"
    id("me.champeau.jmh") version "0.7.2"
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":adapters:persistence-inmemory"))
}

jmh {
    jvmArgsAppend = listOf("-Djmh.separateClasspathJAR=true")
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    timeOnIteration.set("1s")
}

