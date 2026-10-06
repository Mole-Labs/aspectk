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

import com.tschuchort.compiletesting.SourceFile

// Sources shared by the cross-module weaving tests. Every advice appends a tag to shared.CallLog,
// and each test's last module gets a `runTest()` that records the tags of each call separately.

// The `shared` module: the target annotation and the log every advice writes to
val sharedModule =
    "shared" to
        listOf(
            SourceFile.kotlin(
                "Shared.kt",
                """
                package shared

                @Target(AnnotationTarget.FUNCTION)
                annotation class LogCall

                object CallLog {
                    val calls = mutableListOf<String>()

                    // The tags recorded while [block] runs
                    fun record(block: () -> Unit): List<String> {
                        calls.clear()
                        block()
                        return calls.toList()
                    }
                }
                """.trimIndent(),
            ),
        )

// `fun runTest(): List<List<String>>` returning the tags each of [calls] recorded, in order
fun runnerFile(vararg calls: String) = SourceFile.kotlin(
    "Run.kt",
    "fun runTest(): List<List<String>> = listOf(${calls.joinToString { "shared.CallLog.record { $it }" }})\n",
)

// The List<List<String>> returned by the runner file's runTest()
fun ClassLoader.runTest(): Any? = loadClass("RunKt").getMethod("runTest").invoke(null)

// open class <name> [: <parent>] { [@shared.LogCall] open|override fun run() { [super.run()] } }
fun callLogClass(
    name: String,
    parent: String? = null,
    annotated: Boolean = false,
    overridesRun: Boolean = true,
    callsSuper: Boolean = false,
    parentIsInterface: Boolean = false,
): String {
    val supertype = parent?.let { if (parentIsInterface) " : $it" else " : $it()" }.orEmpty()
    if (!overridesRun) return "open class $name$supertype\n"
    val annotation = if (annotated) "    @shared.LogCall\n" else ""
    val modifier = if (parent == null) "open" else "override"
    val body = if (callsSuper) " super.run() " else ""
    return "open class $name$supertype {\n$annotation    $modifier fun run() {$body}\n}\n"
}

fun callLogTarget(className: String = "Target") = """
    class $className {
        @shared.LogCall
        fun run() {}
    }
""".trimIndent()
