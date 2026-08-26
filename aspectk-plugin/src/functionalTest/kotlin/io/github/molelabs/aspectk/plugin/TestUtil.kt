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

// Shared, stable GradleRunner TestKit home (not @TempDir, not fresh per test): a fresh one
// downloads/generates Gradle's own internal jars from scratch every time, and a lingering TestKit
// daemon can still hold file locks right after the build finishes, racing JUnit's own @TempDir
// cleanup -- see aspectk-plugin/build.gradle.kts (functionalTest task).
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

internal fun runGradle(
    projectDir: File,
    testKitDir: File,
    vararg args: String,
): BuildResult = GradleRunner
    .create()
    .withProjectDir(projectDir)
    .withTestKitDir(testKitDir)
    .withArguments("--stacktrace", *args)
    .run()
