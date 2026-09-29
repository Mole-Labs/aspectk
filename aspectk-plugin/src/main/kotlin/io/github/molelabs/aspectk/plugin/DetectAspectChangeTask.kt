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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
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

    // This compilation's hints.json from the previous build
    @get:Internal
    abstract val previousHints: RegularFileProperty

    @TaskAction
    fun detect(inputChanges: InputChanges) {
        // No prior execution state for this task (first run, build-cache miss, --rerun-tasks)
        // means there's no basis to claim "nothing aspect-relevant changed"
        val relevant =
            !inputChanges.isIncremental ||
                    isAspectRelevant(
                        inputChanges.getFileChanges(sources).filter { it.file.extension == "kt" },
                        previousAspects(),
                    )
        val file = resultFile.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(if (relevant) "true:${System.nanoTime()}" else "false")
    }

    // Advice calls already woven into untouched targets depend on aspects Kotlin IC can't see, so a
    // change that adds, edits or removes an aspect must force a full recompile to re-weave them.
    private fun isAspectRelevant(
        changes: List<FileChange>,
        previousAspects: Map<String, Set<String>>,
    ): Boolean {
        val edited = changes.filter { it.changeType != ChangeType.REMOVED && it.file.isFile }
        // An added or edited file carrying a marker, or one that declared an aspect last build even
        // if its markers are gone now
        if (edited.any { change ->
                val text = change.file.readSource()
                ASPECT_RELEVANT_MARKERS.any { marker -> marker in text } ||
                        declaredClasses(text).any { it in previousAspects }
            }
        ) {
            return true
        }
        return changes.any { it.changeType == ChangeType.REMOVED } && deletedAspectStillTargeted(
            previousAspects
        )
    }

    // A deleted file can't be read, so look at what is left: an aspect from the previous build that
    // no remaining source declares was deleted, and its woven calls survive only in remaining
    // sources that still use one of its target annotations.
    private fun deletedAspectStillTargeted(previousAspects: Map<String, Set<String>>): Boolean {
        if (previousAspects.isEmpty()) return false
        val remaining =
            sources.asFileTree.files.filter { it.extension == "kt" }.map { it.readSource() }
        val declared = remaining.flatMapTo(mutableSetOf()) { declaredClasses(it) }
        val orphanedTargets =
            (previousAspects.keys - declared).flatMap { previousAspects.getValue(it) }.toSet()
        if (orphanedTargets.isEmpty()) return false
        val usages = orphanedTargets.map { target ->
            // @LogCall or @some.pkg.LogCall
            Regex("""@(?:[\w.]+\.)?${Regex.escape(target.substringAfterLast('.'))}\b""")
        }
        return remaining.any { text -> usages.any { it.containsMatchIn(text) } }
    }

    private fun previousAspects(): Map<String, Set<String>> {
        val file = previousHints.get().asFile
        if (!file.isFile) return emptyMap()
        val aspects = mutableMapOf<String, MutableSet<String>>()
        // Relies on HintsCodec's record layout: "package", "class", ..., "targets":[...]
        HINT_RECORD.findAll(file.readText()).forEach { match ->
            val (packageName, className, targets) = match.destructured
            aspects.getOrPut(
                qualify(
                    packageName,
                    className.substringBefore('.')
                )
            ) { mutableSetOf() }.addAll(
                QUOTED.findAll(targets).map { it.groupValues[1] }
            )
        }
        return aspects
    }

    private fun File.readSource() = readText()
        .replace(BLOCK_COMMENT, "")
        .replace(LINE_COMMENT, "")

    // Fully qualified names of every class/object/interface declared in [text], nested ones included
    private fun declaredClasses(text: String): Set<String> {
        val packageName = PACKAGE.find(text)?.groupValues?.get(1).orEmpty()
        return DECLARATION.findAll(text).map { qualify(packageName, it.groupValues[1]) }.toSet()
    }

    private fun qualify(
        packageName: String,
        name: String,
    ) = if (packageName.isEmpty()) name else "$packageName.$name"

    companion object {
        private val ASPECT_RELEVANT_MARKERS = listOf("@Aspect", "@Before", "@After", "@Around")

        private val LINE_COMMENT = Regex("//.*")
        private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val PACKAGE = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
        private val DECLARATION = Regex("""\b(?:class|object|interface)\s+(\w+)""")
        private val HINT_RECORD =
            Regex(""""package":"([^"]*)","class":"([^"]*)"[^}]*?"targets":\[([^\]]*)]""")
        private val QUOTED = Regex(""""([^"]*)"""")
    }
}
