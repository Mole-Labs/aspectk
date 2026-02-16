package com.mole.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

@Suppress("unused")
class AspectKBuildPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.add(
            AspectKBuildExtension::class.java,
            "aspectKBuild",
            AspectKBuildExtensionImpl(target),
        )

        commonKotlinConfiguration(target)
    }

    private fun commonKotlinConfiguration(project: Project) {
        project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
            compilerOptions.progressiveMode.convention(true)
        }
    }

    private class AspectKBuildExtensionImpl(
        private val project: Project,
    ) : AspectKBuildExtension {
        override fun publish(pomName: String) {
            TODO("Not yet implemented")
        }

        override fun enableBackwardsCompatibility(
            lowestSupportedKotlinVersion: KotlinVersion,
            lowestSupportedKotlinJvmVersion: KotlinVersion,
        ) {
            project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
                compilerOptions {
                    val actualKotlinVersion =
                        if (this is KotlinJvmCompilerOptions) {
                            lowestSupportedKotlinJvmVersion
                        } else {
                            lowestSupportedKotlinVersion
                        }
                    apiVersion.set(actualKotlinVersion)
                    languageVersion.set(actualKotlinVersion)

                    if (actualKotlinVersion != KotlinVersion.DEFAULT) {
                        progressiveMode.set(false)
                    }
                }
            }
        }
    }
}
