pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal() // only for local published version
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
        mavenLocal() // only for local published version
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "aspectk"
include(":aspectk-core")
include(":aspectk-plugin")
include(":aspectk-runtime")
include(":aspectk-core-tests")
