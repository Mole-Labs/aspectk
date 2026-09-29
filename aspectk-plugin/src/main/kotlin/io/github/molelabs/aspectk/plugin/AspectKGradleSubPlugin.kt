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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal class AspectKGradleSubPlugin : KotlinCompilerPluginSupportPlugin {
    // Adds the aspectk-runtime dependency per compilation instead of per platform-specific
    // extension (multiplatform/Android/JVM source sets). Same approach Metro's Gradle plugin
    // uses: https://github.com/ZacSweers/metro/blob/main/gradle-plugin/src/main/kotlin/dev/zacsweers/metro/gradle/MetroGradleSubplugin.kt
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        val implConfig = kotlinCompilation.defaultSourceSet.implementationConfigurationName
        project.dependencies.add(
            implConfig,
            "${BuildConfig.GROUP}:aspectk-runtime:${BuildConfig.VERSION}",
        )
        if (implConfig == "metadataCompilationImplementation") {
            project.dependencies.add(
                "commonMainImplementation",
                "${BuildConfig.GROUP}:aspectk-runtime:${BuildConfig.VERSION}",
            )
        }

        val hintsDir =
            project.layout.buildDirectory.dir(
                "generated/aspectk/hints/${kotlinCompilation.target.targetName}/${kotlinCompilation.name}",
            )

        kotlinCompilation.compileTaskProvider.configure { task ->
            task.outputs.dir(hintsDir).withPropertyName("aspectkHintsDir")
        }

        val hintsConfiguration = registerHintsConfigurations(project, kotlinCompilation, hintsDir)
        val externalHints = hintsConfiguration.incoming.artifactView { view -> view.isLenient = true }.files

        registerAspectChangeDetection(project, kotlinCompilation, hintsDir, externalHints)

        return project.provider {
            buildList {
                add(SubpluginOption("hintsOutputDir", hintsDir.get().asFile.absolutePath))
                externalHints
                    .forEach { file ->
                        add(SubpluginOption("hintsPath", file.absolutePath))
                    }
            }
        }
    }

    private fun registerHintsConfigurations(
        project: Project,
        kotlinCompilation: KotlinCompilation<*>,
        hintsDir: Provider<Directory>,
    ): Configuration {
        val elementsName = kotlinCompilation.hintsElementsConfigurationName()
        val elementsConfig =
            project.configurations.maybeCreate(elementsName).apply {
                isCanBeConsumed = true
                isCanBeResolved = false
                isVisible = false
            }
        project.artifacts.add(elementsConfig.name, hintsDir) {
            it.builtBy(kotlinCompilation.compileTaskProvider)
        }

        val resolvableName = "${elementsName}Classpath"
        val resolvableConfig =
            project.configurations.maybeCreate(resolvableName).apply {
                isCanBeConsumed = false
                isCanBeResolved = true
                isVisible = false
            }

        project.configurations
            .getByName(kotlinCompilation.compileDependencyConfigurationName)
            .allDependencies
            .withType(ProjectDependency::class.java)
            .configureEach { projectDependency ->
                project.dependencies.add(
                    resolvableConfig.name,
                    project.dependencies.project(
                        mapOf(
                            "path" to projectDependency.path,
                            "configuration" to elementsName,
                        ),
                    ),
                )
            }

        elementsConfig.extendsFrom(resolvableConfig)

        return resolvableConfig
    }

    private fun registerAspectChangeDetection(
        project: Project,
        kotlinCompilation: KotlinCompilation<*>,
        hintsDir: Provider<Directory>,
        externalHints: FileCollection,
    ) {
        val detectTaskName = kotlinCompilation.detectTaskName()
        val changeDir = "generated/aspectk/aspect-change/${kotlinCompilation.target.targetName}"
        val detectTask =
            project.tasks.register(detectTaskName, DetectAspectChangeTask::class.java) { task ->
                task.sources.setFrom(kotlinCompilation.allKotlinSourceSets.map { it.kotlin })
                task.previousHints.set(hintsDir.map { it.file("hints.json") })
                task.resultFile.set(project.layout.buildDirectory.file("$changeDir/${kotlinCompilation.name}.txt"))
            }
        // The detect result this compilation last applied.
        val consumedFileProvider = project.layout.buildDirectory.file("$changeDir/${kotlinCompilation.name}.consumed")

        kotlinCompilation.compileTaskProvider.configure { task ->
            val abstractCompile = task as? AbstractKotlinCompile<*> ?: return@configure
            abstractCompile.dependsOn(detectTask)

            // Declared here, a changed upstream aspect makes Gradle require a full rebuild of this compilation
            abstractCompile.inputs
                .files(externalHints)
                .withPropertyName("aspectkExternalHints")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            val resultFileProvider = detectTask.flatMap { it.resultFile }
            abstractCompile.doFirst {
                val result = resultFileProvider.get().asFile.takeIf { it.exists() }?.readText()
                val consumed = consumedFileProvider.get().asFile.takeIf { it.exists() }?.readText()
                val firstBuild = !hintsDir.get().file("hints.json").asFile.exists()
                // result == consumed: detect was UP-TO-DATE, so an earlier compile already applied
                // this result and no aspect-relevant input changed since.
                if (!firstBuild && result != "false" && result != consumed) {
                    abstractCompile.incremental = false
                }
            }
            // Runs only when compilation succeeded, so a failed build re-applies the same result.
            abstractCompile.doLast {
                val resultFile = resultFileProvider.get().asFile
                if (resultFile.exists()) {
                    consumedFileProvider.get().asFile.writeText(resultFile.readText())
                }
            }
        }
    }

    private fun KotlinCompilation<*>.hintsElementsConfigurationName(): String = "aspectkHints${target.targetName.replaceFirstChar { it.uppercase() }}${name.replaceFirstChar { it.uppercase() }}Elements"

    private fun KotlinCompilation<*>.detectTaskName(): String = "detectAspectChange${
        target.targetName.replaceFirstChar { it.uppercase() }
    }${name.replaceFirstChar { it.uppercase() }}"

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
