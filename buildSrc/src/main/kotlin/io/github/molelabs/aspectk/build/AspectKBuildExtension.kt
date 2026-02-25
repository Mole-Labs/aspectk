package io.github.molelabs.aspectk.build

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

interface AspectKBuildExtension {
    fun publish(pomName: String)

    fun generateBuildConfig(basePackage: String)

    fun enableBackwardsCompatibility(
        // Defaults should generally be the lowest language version still supported by the latest
        lowestSupportedKotlinVersion: KotlinVersion = KotlinVersion.KOTLIN_2_2,
        lowestSupportedKotlinJvmVersion: KotlinVersion = KotlinVersion.KOTLIN_2_2,
    )
}
