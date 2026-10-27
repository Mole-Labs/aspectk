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
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Usage
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
import java.util.concurrent.Callable

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

        val hintsDir = hintsDirOf(project, kotlinCompilation)

        kotlinCompilation.compileTaskProvider.configure { task ->
            task.outputs.dir(hintsDir).withPropertyName("aspectkHintsDir")
        }

        publishHints(project, kotlinCompilation, hintsDir)
        // An associated compilation (test -> main) sees main's declarations without a project
        // dependency, so its hints never arrive through the compile classpath
        val associatedHints =
            project
                .files(project.provider { kotlinCompilation.allAssociatedCompilations.map { hintsDirOf(project, it) } })
                .builtBy(project.provider { kotlinCompilation.allAssociatedCompilations.map { it.compileTaskProvider } })
        val externalHints = consumeHints(project, kotlinCompilation) + associatedHints

        registerAspectChangeDetection(project, kotlinCompilation, hintsDir, externalHints)

        val extension = project.extensions.getByType(AspectKGradleExtension::class.java)

        return project.provider {
            buildList {
                add(SubpluginOption("debug", extension.debug.get().toString()))
                add(SubpluginOption("hintsOutputDir", hintsDir.get().asFile.absolutePath))
                externalHints
                    .forEach { file ->
                        add(SubpluginOption("hintsPath", file.absolutePath))
                    }
            }
        }
    }

    // Offers this compilation's hints next to the classes it already offers: a secondary variant of
    // the configuration other projects resolve their compile classpath against. Which variant of
    // this project a consumer gets is then decided by the same matching that picks its classes, so
    // an application variant `playDebug` finds a library's `debug` without the names agreeing.
    private fun publishHints(
        project: Project,
        kotlinCompilation: KotlinCompilation<*>,
        hintsDir: Provider<Directory>,
    ) {
        project.configurations
            .matching { it.name in kotlinCompilation.apiElementsCandidates() }
            .configureEach { apiElements ->
                apiElements.outgoing.variants.maybeCreate(HINTS_VARIANT).apply {
                    // Its own usage keeps it out of ordinary resolution: asked for classes, Gradle
                    // would otherwise find two candidates here and refuse to choose.
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, project.hintsUsage())
                    attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, HINTS_ARTIFACT_TYPE)
                    artifact(hintsDir) { it.builtBy(kotlinCompilation.compileTaskProvider) }
                }
            }
    }

    // The hints of everything on this compilation's compile classpath. A dependency that does not
    // apply this plugin offers no such artifact and is left out, which a view with attributes allows.
    private fun consumeHints(
        project: Project,
        kotlinCompilation: KotlinCompilation<*>,
    ): FileCollection = project.configurations
        .getByName(kotlinCompilation.compileDependencyConfigurationName)
        .incoming
        .artifactView { view ->
            view.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.hintsUsage())
            view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, HINTS_ARTIFACT_TYPE)
        }.files

    private fun Project.hintsUsage(): Usage = objects.named(Usage::class.java, HINTS_ARTIFACT_TYPE)

    // An Android variant is published as `<variant>ApiElements`; every other target publishes its
    // main compilation as the target's own api elements.
    private fun KotlinCompilation<*>.apiElementsCandidates(): Set<String> = buildSet {
        add("${name}ApiElements")
        if (name == KotlinCompilation.MAIN_COMPILATION_NAME) add(target.apiElementsConfigurationName)
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
                // AGP's built-in Kotlin compiles sources that no Kotlin source set knows about.
                // Through a Callable and not the task provider, which would make this task depend on the
                // compilation it has to run before. for AbstractKotlinNativeCompile, DetectAspectChangeTask is't needed
                task.sources.setFrom(
                    Callable {
                        (kotlinCompilation.compileTaskProvider.get() as? AbstractKotlinCompile<*>)?.sources ?: project.files()
                    },
                )
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

    private fun hintsDirOf(
        project: Project,
        compilation: KotlinCompilation<*>,
    ): Provider<Directory> = project.layout.buildDirectory.dir("generated/aspectk/hints/${compilation.target.targetName}/${compilation.name}")

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
        target.extensions
            .create(EXTENSION_NAME, AspectKGradleExtension::class.java)
            .debug
            .convention(false)
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

    private companion object {
        const val EXTENSION_NAME = "aspectk"
        const val HINTS_VARIANT = "aspectkHints"
        const val HINTS_ARTIFACT_TYPE = "aspectk-hints"
    }
}
