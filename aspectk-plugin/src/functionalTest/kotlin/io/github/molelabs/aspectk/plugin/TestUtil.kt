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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

internal fun testRepo(): String = System.getProperty("aspectk.testRepo")

internal fun aspectkVersion(): String = System.getProperty("aspectk.version")

internal fun kotlinVersion(): String = System.getProperty("aspectk.kotlinVersion")

internal fun testKitDir(): File = File(System.getProperty("aspectk.testKitHome"))

internal fun writeFile(
    projectDir: File,
    relativePath: String,
    content: String,
) {
    File(projectDir, relativePath).apply {
        parentFile.mkdirs()
        writeText(content)
    }
}

internal fun removeFile(
    projectDir: File,
    relativePath: String,
) {
    File(projectDir, relativePath).delete()
}

internal fun hintsOf(
    projectDir: File,
    module: String,
    compilation: String = "main",
): String = File(projectDir, "$module/build/generated/aspectk/hints")
    .walkTopDown()
    .firstOrNull { it.name == "hints.json" && it.parentFile.name == compilation }
    ?.readText()
    ?: error("no $compilation hints.json in $module")

internal fun runGradle(
    projectDir: File,
    testKitDir: File,
    vararg args: String,
): BuildResult = GradleRunner
    .create()
    .withProjectDir(projectDir)
    .withTestKitDir(testKitDir)
    .withDebug(true)
    .withArguments("--stacktrace", *args)
    .run()

// Multi-module project: one module per (name, dependencies-block) pair
internal fun writeProject(
    projectDir: File,
    vararg modules: Pair<String, String>,
) {
    writeFile(
        projectDir,
        "settings.gradle.kts",
        """
        pluginManagement {
            repositories {
                maven(url = "${testRepo()}")
                gradlePluginPortal()
                mavenCentral()
            }
        }
        dependencyResolutionManagement {
            repositories {
                maven(url = "${testRepo()}")
                mavenCentral()
            }
        }
        rootProject.name = "functional-test"
        """.trimIndent() + modules.joinToString("") { "\ninclude(\":${it.first}\")" },
    )
    modules.forEach { (name, dependencies) ->
        writeFile(projectDir, "$name/build.gradle.kts", moduleBuildFile(dependencies))
    }
}

internal fun moduleBuildFile(dependencies: String) = """
plugins {
    id("org.jetbrains.kotlin.jvm") version "${kotlinVersion()}"
    id("io.github.mole-labs.aspectk") version "${aspectkVersion()}"
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
$dependencies
}
tasks.test {
    useJUnitPlatform()
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

$DUMP_HINTS_PATH_TASK
"""

private val DUMP_HINTS_PATH_TASK = """
val aspectkHintsPath = files(
    provider {
        configurations
            .filter { it.name.startsWith("aspectkHints") && it.name.endsWith("MainElementsClasspath") }
            .map { it.incoming.artifactView { isLenient = true }.files }
    },
)
tasks.register("dumpHintsPath") {
    // The provider above carries no task dependencies; once this module has compiled, every upstream
    // hints.json it consumed is final for this build.
    dependsOn("compileKotlin")
    val out = layout.buildDirectory.file("hints-path.txt")
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        out.get().asFile.writeText(
            aspectkHintsPath.files.joinToString("\n") { dir ->
                val hints = dir.resolve("hints.json").takeIf { it.exists() }?.readText() ?: "<missing>"
                dir.relativeTo(rootDir).invariantSeparatorsPath + "\t" + hints
            },
        )
    }
}
""".trim()
