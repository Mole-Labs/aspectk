import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.diffplug.spotless")
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.shadow)
    kotlin("kapt")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.0.1")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }

    java {
        target("src/**/*.java")
        googleJavaFormat()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

group = "com.mole"
version = "unspecified"

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    compileOnly(libs.google.autoservice.annotations)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin.api)
    testRuntimeOnly(libs.kotlin.compiler)
    testImplementation(project(":runtime"))
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.test)
    kapt(libs.google.autoservice)
}

val embedded by configurations.dependencyScope("embedded")

val embeddedClasspath by configurations.resolvable("embeddedClasspath") { extendsFrom(embedded) }

tasks.named<ShadowJar>("shadowJar") {
    from(java.sourceSets.main.map { it.output })
    configurations.add(embeddedClasspath)

    dependencies {
        exclude(dependency("org.jetbrains:.*"))
        exclude(dependency("org.intellij:.*"))
        exclude(dependency("org.jetbrains.kotlin:.*"))
    }

    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
