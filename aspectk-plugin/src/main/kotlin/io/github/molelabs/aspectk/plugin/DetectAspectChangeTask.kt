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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

// Decides whether THIS round needs a full recompile
// For single-module setups, triggers a recompilation
// when changes to AspectK Runtime annotations are detected.
// TODO migrate to PredicateBasedProvider
internal abstract class DetectAspectChangeTask : DefaultTask() {
    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val resultFile: RegularFileProperty

    @TaskAction
    fun detect(inputChanges: InputChanges) {
        // No prior execution state for this task (first run, build-cache miss, --rerun-tasks)
        // means there's no basis to claim "nothing aspect-relevant changed"
        val relevant =
            !inputChanges.isIncremental ||
                inputChanges.getFileChanges(sources).any { change ->
                    if (change.file.extension != "kt") {
                        false
                    } else {
                        change.file.isFile && fileHasAspectMarker(change.file)
                    }
                }
        val file = resultFile.get().asFile
        file.parentFile?.mkdirs()
        // Unique per execution, so the compile task can tell a fresh result from one it already applied
        file.writeText(if (relevant) "true:${System.nanoTime()}" else "false")
    }

    private fun fileHasAspectMarker(file: File): Boolean {
        val text = file.readText()
            .replace(BLOCK_COMMENT, "")
            .replace(LINE_COMMENT, "")
        return ASPECT_RELEVANT_MARKERS.any { marker -> marker in text }
    }

    companion object {
        private val ASPECT_RELEVANT_MARKERS = listOf("@Aspect", "@Before", "@After", "@Around")

        private val LINE_COMMENT = Regex("//.*")
        private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    }
}
