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
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
    id("io.github.mole-labs.aspectk.build")
    kotlin("kapt")
    `java-gradle-plugin`
}

aspectKBuild {
    publish("AspectK Gradle Plugin")
    generateBuildConfig("io.github.molelabs.aspectk.plugin")
    enableBackwardsCompatibility()
}

gradlePlugin {
    this.plugins {
        create("aspectk") {
            id = "io.github.mole-labs.aspectk"
            implementationClass = "io.github.molelabs.aspectk.plugin.AspectKGradleSubPlugin"
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin.api)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

// Functional tests drive real Gradle builds (GradleRunner) against the plugin as actually
// published, so they can exercise things unit tests can't: real incremental compilation
// across separate invocations, real ServiceLoader discovery, real Configuration resolution.
sourceSets {
    create("functionalTest") {
        kotlin.srcDir("src/functionalTest/kotlin")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["functionalTestImplementation"].extendsFrom(configurations["implementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

dependencies {
    add("functionalTestImplementation", gradleTestKit())
    add("functionalTestImplementation", libs.junit.jupiter)
}

// Publishes the current working tree to the same throwaway "testing" repo the release
// pipeline already defines (AspectKBuildPlugin.publish() -> build/localMaven), so a
// functional test project can consume aspectk-core/aspectk-runtime/aspectk-plugin by
// applying `id("io.github.mole-labs.aspectk") version PUBLISH_VERSION` without touching
// mavenLocal()/~/.m2 (avoids the immutable-non-SNAPSHOT-version caching trap).
val publishForFunctionalTest =
    listOf(
        ":aspectk-runtime:publishAllPublicationsToTestingRepository",
        ":aspectk-core:publishAllPublicationsToTestingRepository",
        ":aspectk-plugin:publishAllPublicationsToTestingRepository",
    )

val functionalTest by tasks.registering(Test::class) {
    description = "Runs functional tests against real Gradle builds."
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
    dependsOn(publishForFunctionalTest)
    systemProperty(
        "aspectk.testRepo",
        rootProject.layout.buildDirectory.dir("localMaven").get().asFile.absolutePath,
    )
    systemProperty("aspectk.version", project.property("PUBLISH_VERSION") as String)
    systemProperty("aspectk.kotlinVersion", libs.versions.kotlin.get())
    // A stable, shared GradleRunner TestKit home instead of a fresh one per test: a fresh one
    // downloads/generates Gradle's own internal jars from scratch every time (~350MB each) and,
    // since it must NOT be JUnit-@TempDir-cleaned (a lingering TestKit daemon can still hold
    // locks right after the build finishes, racing JUnit's own cleanup), that used to just pile
    // up on disk across runs.
    systemProperty(
        "aspectk.testKitHome",
        rootProject.layout.buildDirectory.dir("functionalTestGradleHome").get().asFile.absolutePath,
    )
}
