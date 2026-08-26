pluginManagement {
    repositories {
        // Throwaway repo the root aspectk build publishes an unreleased PUBLISH_VERSION to
        // (AspectKBuildPlugin.publish() -> build/localMaven) -- see :core's build.gradle.kts
        // for why this is used instead of mavenLocal().
        maven(url = "../build/localMaven")
        mavenCentral()
        mavenLocal()
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
        maven(url = "../build/localMaven")
        mavenCentral()
        mavenLocal()
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "aspectk-sample"
include(":composeApp")
include(":core")
include(":data")
include(":feature-user")
include(":feature-payment")
include(":feature-catalog")
