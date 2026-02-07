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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.diffplug.spotless)
    kotlin("kapt")
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
    testImplementation(libs.test.mockk)
    testImplementation(libs.kotlin.coroutine.core)
    testImplementation(libs.kotlin.coroutine.test)
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
