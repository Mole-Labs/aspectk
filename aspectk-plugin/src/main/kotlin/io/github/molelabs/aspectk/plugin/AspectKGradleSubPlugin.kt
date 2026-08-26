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
        project.dependencies.add(implConfig, "${BuildConfig.GROUP}:aspectk-runtime:${BuildConfig.VERSION}")
        if (implConfig == "metadataCompilationImplementation") {
            project.dependencies.add("commonMainImplementation", "${BuildConfig.GROUP}:aspectk-runtime:${BuildConfig.VERSION}")
        }

        val hintsDir =
            project.layout.buildDirectory.dir(
                "generated/aspectk/hints/${kotlinCompilation.target.targetName}/${kotlinCompilation.name}",
            )

        kotlinCompilation.compileTaskProvider.configure { task ->
            task.outputs.dir(hintsDir).withPropertyName("aspectkHintsDir")
        }

        val hintsConfiguration = registerHintsConfigurations(project, kotlinCompilation, hintsDir)
        registerAspectChangeDetection(project, kotlinCompilation)

        return project.provider {
            buildList {
                add(SubpluginOption("hintsOutputDir", hintsDir.get().asFile.absolutePath))
                hintsConfiguration.incoming
                    .artifactView { view -> view.isLenient = true }
                    .files
                    .forEach { file ->
                        add(SubpluginOption("hintsPath", file.absolutePath))
                    }
            }
        }
    }

    // Naming is a pure function of (targetName, compilationName) alone, so a downstream
    // project can name the exact configuration it needs on an upstream project without any
    // cross-project introspection. This only propagates automatically to/from other projects
    // that also apply this Gradle plugin with a matching target+compilation name — see
    // docs/design-decision/cross-module-weaving.md §3 "What we're giving up".
    private fun hintsElementsConfigurationName(
        targetName: String,
        compilationName: String,
    ): String = "aspectkHints${targetName.replaceFirstChar { it.uppercase() }}${compilationName.replaceFirstChar { it.uppercase() }}Elements"

    private fun registerHintsConfigurations(
        project: Project,
        kotlinCompilation: KotlinCompilation<*>,
        hintsDir: Provider<Directory>,
    ): Configuration {
        val elementsName = hintsElementsConfigurationName(kotlinCompilation.target.targetName, kotlinCompilation.name)
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

        // Mirror this compilation's own project dependencies, but pointed at the SAME named
        // hints-elements configuration on each dependency project instead of its default variant.
        // Because that configuration on the dependency project is wired the same recursive way,
        // Gradle's ordinary configuration-graph resolution walks and dedups the rest transitively
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

        // Without this, elementsConfig only ever exposes THIS module's own hintsDir
        elementsConfig.extendsFrom(resolvableConfig)

        return resolvableConfig
    }

    // Weaving requires the target and its advice in the same incremental round. A target-only edit
    // is fine -- AspectKIrCompilerContext.visitedAspectClassIds carries the aspect's last-known
    // hints forward. An aspect-only edit is not: the target files needing a re-weave aren't in a
    // round that doesn't touch them, and choosing what to compile is above the plugin's reach.
    // So DetectAspectChangeTask forces a full recompile when a file that CHANGED THIS ROUND mentions
    // @Aspect/@Before/@After/@Around -- via Gradle's InputChanges, not "does this compilation contain
    // one anywhere", which would mean a full recompile forever once a module uses AspectK.
    // Err conservative: a false positive costs one extra compile, a false negative silently ships
    // unwoven bytecode. See AspectChangeDetection.kt and docs/design-decision/cross-module-weaving.md.
    private fun registerAspectChangeDetection(
        project: Project,
        kotlinCompilation: KotlinCompilation<*>,
    ) {
        val sources: FileCollection =
            kotlinCompilation.allKotlinSourceSets.fold(project.files() as FileCollection) { acc, sourceSet ->
                acc + sourceSet.kotlin
            }

        val detectTaskName =
            "detectAspectChange${
                kotlinCompilation.target.targetName.replaceFirstChar { it.uppercase() }
            }${kotlinCompilation.name.replaceFirstChar { it.uppercase() }}"
        val detectTask =
            project.tasks.register(detectTaskName, DetectAspectChangeTask::class.java) { task ->
                task.sources.setFrom(sources)
                task.resultFile.set(
                    project.layout.buildDirectory.file(
                        "generated/aspectk/aspect-change/${kotlinCompilation.target.targetName}/${kotlinCompilation.name}.txt",
                    ),
                )
            }

        kotlinCompilation.compileTaskProvider.configure { task ->
            val abstractCompile = task as? AbstractKotlinCompile<*> ?: return@configure
            abstractCompile.dependsOn(detectTask)
            val resultFileProvider = detectTask.flatMap { it.resultFile }
            abstractCompile.doFirst {
                val resultFile = resultFileProvider.get().asFile
                if (!resultFile.exists() || resultFile.readText() != "false") {
                    abstractCompile.incremental = false
                }
            }
        }
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
