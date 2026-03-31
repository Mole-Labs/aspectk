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

import org.gradle.internal.extensions.core.serviceOf
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
    id("kotlin-conventions")
    id("native-conventions")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlin.coroutine.core)
        implementation(libs.kotlin.coroutine.test)
        implementation(project(":aspectk-runtime"))
    }
}

dependencies {
    add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":aspectk-core"))
    add(
        NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME,
        project(":aspectk-core"),
    )
}

tasks.register("testAllSupportedVersions") {
    notCompatibleWithConfigurationCache("Runs separate Gradle invocations per Kotlin version")
    doLast {
        val execOps = serviceOf<ExecOperations>()
        val versions =
            rootDir
                .resolve("supported-versions.txt")
                .readLines()
                .filter { it.isNotBlank() }
        versions.forEach { version ->
            println("Testing Kotlin version: $version")
            execOps.exec {
                commandLine(
                    rootDir.resolve("gradlew").path,
                    ":aspectk-core-tests:jvmTest",
                    "-PkotlinVersion=$version",
                    "--no-configuration-cache",
                )
                workingDir = rootDir
            }
        }
    }
}
