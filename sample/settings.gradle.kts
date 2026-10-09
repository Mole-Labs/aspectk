pluginManagement {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "aspectk-sample"

// The sample builds against the aspectk sources in the parent directory, so nothing has to be
// published first and a change in the library shows up here right away. Gradle finds the Gradle
// plugin by its id, and substitutes the compiler plugin and the runtime for the published
// coordinates the Gradle plugin adds them by (the modules' `group` matches the published one).
includeBuild("..")

include(":composeApp")
include(":core")
include(":data")
include(":feature-user")
include(":feature-payment")
include(":feature-catalog")
