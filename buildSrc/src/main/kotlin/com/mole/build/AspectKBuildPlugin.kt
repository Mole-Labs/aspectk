package com.mole.build

import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.buildConfigField
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private val Project.aspectKGroupId get() = property("PUBLISH_GROUP") as String
private val Project.aspectKVersion get() = property("PUBLISH_VERSION") as String

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
            project.pluginManager.apply("com.vanniktech.maven.publish")

            val publishing = project.extensions.getByName("publishing") as PublishingExtension
            publishing.repositories {
                maven {
                    name = "testing"
                    setUrl(
                        project.rootProject.layout.buildDirectory
                            .dir("localMaven"),
                    )
                }
            }

            val mavenPublishing =
                project.extensions.getByName("mavenPublishing") as MavenPublishBaseExtension
            mavenPublishing.apply {
                coordinates(project.aspectKGroupId, project.name, project.aspectKVersion)

                pom {
                    name.set(pomName)
                    description.set(
                        "A Simple AOP Library Similar as AspectJ works on Kotlin Multiplatform",
                    )
                    url.set("https://github.com/Mole-Labs/aspectk")

                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }

                    scm {
                        url.set("https://github.com/Mole-Labs/aspectk/tree/main")
                        connection.set("scm:git:github.com:Mole-Labs/aspectk.git")
                        developerConnection.set("scm:git:ssh://github.com:Mole-Labs/aspectk.git")
                    }

                    developers {
                        developer {
                            id.set("oungsi2000")
                            name.set("YongJun Jung")
                            email.set("oungsi1000@gmail.com")
                        }
                    }
                }
                signAllPublications()
                publishToMavenCentral(
                    automaticRelease = true,
                )
            }
//            project.pluginManager.apply("org.jetbrains.dokka")
            project.pluginManager.apply("org.jetbrains.kotlinx.binary-compatibility-validator")

            // Published modules should be explicit about their API visibility.
            val kotlinPluginHandler =
                Action<AppliedPlugin> {
                    val kotlin = project.extensions.getByType<KotlinBaseExtension>()
                    kotlin.explicitApi()
                }
            project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm", kotlinPluginHandler)
            project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform", kotlinPluginHandler)
        }

        override fun generateBuildConfig(basePackage: String) {
            project.pluginManager.apply("com.github.gmazzo.buildconfig")

            val buildConfig = project.extensions.getByName("buildConfig") as BuildConfigExtension
            buildConfig.apply {
                packageName(basePackage)
                buildConfigField("GROUP", project.aspectKGroupId)
                buildConfigField("VERSION", project.aspectKVersion)
                buildConfigField("COMPILER_PLUGIN_ID", "com.mole.aspectk")
                buildConfigField("COMPILER_PLUGIN_ARTIFACT", "core")
            }
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
