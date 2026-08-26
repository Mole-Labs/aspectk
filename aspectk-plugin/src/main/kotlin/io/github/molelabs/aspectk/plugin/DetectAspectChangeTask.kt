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
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

// Decides whether THIS round needs a full (non-incremental) recompile, based only on the files
// that actually changed since this task's own last successful run -- not "does the compilation
// contain an @Aspect/@Before/@After/@Around anywhere" (that would force a full recompile on every
// edit, forever, once a module uses AspectK at all), but "did one of the files that changed just
// now mention one" (docs/design-decision/cross-module-weaving.md). The result is written to
// resultFile rather than communicated via a live reference to this task, so the consuming compile
// task (AspectKGradleSubPlugin.registerAspectChangeDetection) can read the decision from disk in
// its own doFirst without holding a Task reference across a configuration-cache boundary.
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
                        when (change.changeType) {
                            ChangeType.MODIFIED, ChangeType.ADDED -> change.file.isFile && fileHasAspectMarker(change.file)
                            ChangeType.REMOVED -> true
                        }
                    }
                }
        val file = resultFile.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(relevant.toString())
    }
}
