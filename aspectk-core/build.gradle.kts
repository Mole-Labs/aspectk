/*
 * Copyright (C) 2026 aspectk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("io.github.mole-labs.aspectk.build")
    alias(libs.plugins.diffplug.spotless)
    alias(libs.plugins.shadow)
    kotlin("kapt")
}

aspectKBuild {
    publish("AspectK Compiler Plugin")
    generateBuildConfig("io.github.molelabs.aspectk.core")
    enableBackwardsCompatibility()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

group = "com.mole"
version = "unspecified"

repositories {
    google()
    mavenCentral()
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    compileOnly(libs.google.autoservice.annotations)
    implementation(project(":aspectk-runtime"))
    implementation(project(":aspectk-core-compat"))
    runtimeOnly(project(":aspectk-core-compat:compat-2220"))
    runtimeOnly(project(":aspectk-core-compat:compat-2310"))
    runtimeOnly(project(":aspectk-core-compat:compat-2320"))

    shadowBundle(project(":aspectk-core-compat"))
    shadowBundle(project(":aspectk-core-compat:compat-2220"))
    shadowBundle(project(":aspectk-core-compat:compat-2310"))
    shadowBundle(project(":aspectk-core-compat:compat-2320"))

    testRuntimeOnly(libs.kotlin.compiler)
    testImplementation(libs.test.mockk)
    testImplementation(libs.kotlin.coroutine.core)
    testImplementation(libs.kotlin.coroutine.test)
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.test)
    kapt(libs.google.autoservice)
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("")
    mergeServiceFiles()
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains:.*"))
        exclude(dependency("org.intellij:.*"))
    }
}

tasks.jar {
    enabled = false
}

listOf("runtimeElements", "apiElements").forEach { variantName ->
    configurations.named(variantName) {
        outgoing.artifacts.removeIf { it.type == "jar" }
        outgoing.artifact(tasks.shadowJar)
    }
}

tasks.test {
    useJUnitPlatform()
}
