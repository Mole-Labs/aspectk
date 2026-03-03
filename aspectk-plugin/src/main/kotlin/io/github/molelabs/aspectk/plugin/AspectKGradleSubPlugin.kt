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

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

internal class AspectKGradleSubPlugin : KotlinCompilerPluginSupportPlugin {
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider { emptyList() }
    }

    override fun getCompilerPluginId(): String = BuildConfig.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = BuildConfig.GROUP,
            artifactId = BuildConfig.COMPILER_PLUGIN_ARTIFACT,
            version = BuildConfig.VERSION,
        )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun apply(target: Project) {
        val aspectKRuntimeDependency =
            target.provider {
                target.dependencyFactory.create(BuildConfig.GROUP, "aspectk-runtime", BuildConfig.VERSION)
            }

        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.extensions.getByName("kotlin") as KotlinSourceSetContainer
            val commonMainSourceSet = kotlin.sourceSets.getByName(COMMON_MAIN_SOURCE_SET_NAME)

            target.configurations.named(commonMainSourceSet.implementationConfigurationName).configure {
                it.dependencies.addLater(aspectKRuntimeDependency)
            }
        }

        target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val sourceSets = target.extensions.getByName("sourceSets") as SourceSetContainer
            sourceSets.configureEach { sourceSet ->
                target.configurations.named(sourceSet.implementationConfigurationName).configure {
                    it.dependencies.addLater(aspectKRuntimeDependency)
                }
            }
        }
    }
}
