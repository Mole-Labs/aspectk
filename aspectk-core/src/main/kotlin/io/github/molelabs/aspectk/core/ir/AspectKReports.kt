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
package io.github.molelabs.aspectk.core.ir

import io.github.molelabs.aspectk.core.hints.HintRecord
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.io.File

internal fun List<HintRecord>.withOrigin(origin: String): List<Pair<HintRecord, String>> = map { it to origin }

// aspects.txt: every advice each target ends up with, and the hints that failed to resolve.
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun writeReports(
    dir: File,
    compiledFiles: List<IrFile>,
    lookUp: AspectLookUp,
    resolved: List<Pair<HintRecord, String>>,
    dropped: List<Pair<HintRecord, String>>,
) {
    // Advice not among the resolved hints was collected from this compilation's sources
    val origins = resolved.associate { (hint, origin) -> hint.key to origin }
    val label = { context: AspectContext ->
        val classId = context.aspect.owner.classId
        val advice = context.advice.owner.name.asString()
        "${context.kind}  ${classId?.asFqNameString()}.$advice  [${origins[classId to advice] ?: "local"}]"
    }

    dir.mkdirs()
    File(dir, "aspects.txt").writeText(
        buildString {
            appendLine("## Applied")
            lookUp.targets.sortedBy { it.asString() }.forEach { target ->
                appendLine(target.asString())
                lookUp[target].forEach { appendLine("  ${label(it)}") }
            }
            appendLine()
            appendLine("## Dropped")
            dropped.forEach { (hint, origin) ->
                appendLine("${hint.key.first.asFqNameString()}.${hint.functionName} -> ${hint.targets.joinToString()}  [$origin]")
            }
        },
    )

    // A recompiled file may no longer be woven, so clear what an earlier round wrote for it
    compiledFiles.forEach { file -> file.reportFiles(dir).toList().forEach(File::delete) }

    lookUp.wovenFiles.forEach { (file, woven) ->
        val (dump, aspects) = file.reportFiles(dir)
        dump.parentFile.mkdirs()
        dump.writeText(file.dumpKotlinLike())
        aspects.writeText(
            buildString {
                woven.groupBy({ it.first }, { it.second }).forEach { (function, contexts) ->
                    appendLine(function.fqNameWhenAvailable?.asString())
                    contexts.forEach { appendLine("  ${label(it)}") }
                }
            },
        )
    }
}

private fun IrFile.reportFiles(dir: File): Pair<File, File> {
    val packageDir = File(dir, "woven/${packageFqName.asString().replace('.', '/')}")
    return File(packageDir, name) to File(packageDir, "${name.substringBeforeLast('.')}.aspects.txt")
}

private val HintRecord.key: Pair<ClassId, String>
    get() = ClassId(FqName(packageName), FqName(className), false) to functionName
