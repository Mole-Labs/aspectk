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
package io.github.molelabs.aspectk.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal class AspectKGradleSubPlugin : KotlinCompilerPluginSupportPlugin {
    // Adds the aspectk-runtime dependency per compilation instead of per platform-specific
    // extension (multiplatform/Android/JVM source sets). Same approach Metro's Gradle plugin
    // uses: https://github.com/ZacSweers/metro/blob/main/gradle-plugin/src/main/kotlin/dev/zacsweers/metro/gradle/MetroGradleSubplugin.kt
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        val implConfig = kotlinCompilation.defaultSourceSet.implementationConfigurationName
        project.dependencies.add(implConfig, "${BuildConfig.GROUP}:aspectk-runtime:${BuildConfig.VERSION}")
        if (implConfig == "metadataCompilationImplementation") {
            project.dependencies.add("commonMainImplementation", "${BuildConfig.GROUP}:aspectk-runtime:${BuildConfig.VERSION}")
        }

        return project.provider { emptyList() }
    }

    override fun getCompilerPluginId(): String = BuildConfig.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.GROUP,
        artifactId = BuildConfig.COMPILER_PLUGIN_ARTIFACT,
        version = BuildConfig.VERSION,
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    @OptIn(ExperimentalBuildToolsApi::class, ExperimentalKotlinGradlePluginApi::class)
    override fun apply(target: Project) {
        GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE
        val compilerVersionProvider =
            target.kotlinExtension.compilerVersion.map { KotlinToolingVersion(it) }
                ?: target.provider { target.kotlinToolingVersion }

        val compilerVersion = compilerVersionProvider.get()
        val supportedVersions = BuildConfig.SUPPORTED_KOTLIN_VERSIONS.map(::KotlinToolingVersion)
        val minSupported = supportedVersions.min()
        val maxSupported = supportedVersions.max()
        val isSupported = compilerVersion in minSupported..maxSupported

        if (!isSupported) {
            if (compilerVersion < minSupported) {
                throw GradleException(
                    """
                    "AspectK '${BuildConfig.VERSION} requires Kotlin ${BuildConfig.SUPPORTED_KOTLIN_VERSIONS.first()} or later, but this build uses $compilerVersion"
                    "Supported Kotlin versions: ${BuildConfig.SUPPORTED_KOTLIN_VERSIONS.first()} - ${BuildConfig.SUPPORTED_KOTLIN_VERSIONS.last()}"
                    """.trimIndent(),
                )
            } else {
                throw GradleException(
                    """
                    This build uses unrecognized Kotlin version '$compilerVersion"
                    "Supported Kotlin versions: ${BuildConfig.SUPPORTED_KOTLIN_VERSIONS.first()} - ${BuildConfig.SUPPORTED_KOTLIN_VERSIONS.last()}"
                    """.trimIndent(),
                )
            }
        }
    }
}
