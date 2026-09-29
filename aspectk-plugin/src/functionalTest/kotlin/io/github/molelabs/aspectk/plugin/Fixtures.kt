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

// declaresAnnotation = false leaves LogCall<postFix> to another file, so deleting this aspect file
// keeps its targets compiling
fun aspectFile(
    postFix: String = "",
    declaresAnnotation: Boolean = true,
) = """
        import io.github.molelabs.aspectk.runtime.Aspect
        import io.github.molelabs.aspectk.runtime.Before
        import io.github.molelabs.aspectk.runtime.JoinPoint

        ${if (declaresAnnotation) "annotation class LogCall$postFix" else ""}

        @Aspect
        object LoggingAspect$postFix {
            var executionCount: Int = 0

            @Before(LogCall$postFix::class)
            fun log(joinPoint: JoinPoint) {
                executionCount++
            }
        }
""".trimIndent()

fun targetFile(postFix: String = "") = """
        class Target$postFix {
            @LogCall$postFix
            fun run() {}
        }
""".trimIndent()

fun targetFileWithoutAnnotation() = """
        class Target {
            fun run() {}
        }
""".trimIndent()

fun aspectFileWithoutAdvice(postFix: String = "") = """
        import io.github.molelabs.aspectk.runtime.Aspect

        annotation class LogCall$postFix

        @Aspect
        object LoggingAspect$postFix {
            var executionCount: Int = 0
        }
""".trimIndent()

// Only calls the target: for when the aspect it was woven against no longer exists and cannot be
// referenced, passing means the stale woven call is gone
fun targetRunsTestFile(postFix: String = "") = """
        import org.junit.jupiter.api.Test

        class TargetRunsTest$postFix {
            @Test
            fun `target runs`() {
                Target$postFix().run()
            }
        }
""".trimIndent()

fun weavingTestFile(executionCount: Int = 1, postFix: String = "") = """
        import org.junit.jupiter.api.Assertions.assertEquals
        import org.junit.jupiter.api.Test

        class WeavingTest$postFix {
            @Test
            fun `advice fires`() {
                LoggingAspect$postFix.executionCount = 0
                Target$postFix().run()
                assertEquals($executionCount, LoggingAspect$postFix.executionCount)
            }
        }
""".trimIndent()

fun multipleAspectFile() = """
        import io.github.molelabs.aspectk.runtime.Aspect
        import io.github.molelabs.aspectk.runtime.Before
        import io.github.molelabs.aspectk.runtime.Around
        import io.github.molelabs.aspectk.runtime.JoinPoint

        annotation class LogCall

        @Aspect
        object LoggingAspect {
            var executionCount: Int = 0

            @Before(LogCall::class)
            fun log(joinPoint: JoinPoint) {
                executionCount++
            }

            @Around(LogCall::class)
            fun logAround(joinPoint: JoinPoint) {
                executionCount++
            }
        }
""".trimIndent()

fun aspectFileWithoutMarkers() = """
        import io.github.molelabs.aspectk.runtime.JoinPoint

        annotation class LogCall

        object LoggingAspect {
            var executionCount: Int = 0

            fun log(joinPoint: JoinPoint) {
                executionCount++
            }
        }
""".trimIndent()

// same shape as aspectFile(), but reads Shared.tag so IC recompiles it when Shared changes
fun aspectDependingOnSharedFile(postFix: String = "") = """
        import io.github.molelabs.aspectk.runtime.Aspect
        import io.github.molelabs.aspectk.runtime.Before
        import io.github.molelabs.aspectk.runtime.JoinPoint

        annotation class LogCall$postFix

        @Aspect
        object LoggingAspect$postFix {
            @Before(LogCall$postFix::class)
            fun log(joinPoint: JoinPoint) {
                println(Shared.tag)
            }
        }
""".trimIndent()

fun mixedAspectFile() = """
        package com.example.aspect

        import io.github.molelabs.aspectk.runtime.After
        import io.github.molelabs.aspectk.runtime.Aspect
        import io.github.molelabs.aspectk.runtime.Before
        import io.github.molelabs.aspectk.runtime.JoinPoint

        annotation class First
        annotation class Second

        @Aspect
        object MixedAspect {
            @Before(First::class, Second::class)
            fun before(joinPoint: JoinPoint) {}

            @After(First::class, inherits = true)
            fun after(joinPoint: JoinPoint) {}
        }
""".trimIndent()

fun sharedModuleFile() = """
        package shared

        annotation class LogCall

        object CallLog {
            val calls = mutableListOf<String>()
        }
""".trimIndent()

fun callLogAspectFile(
    name: String,
    tag: String,
    pkg: String? = null,
    targets: List<String> = listOf("shared.LogCall"),
    inherits: Boolean = false,
    withMethodName: Boolean = false,
) = """
    ${pkg?.let { "package $it\n" }.orEmpty()}
    import io.github.molelabs.aspectk.runtime.Aspect
    import io.github.molelabs.aspectk.runtime.Before
    import io.github.molelabs.aspectk.runtime.JoinPoint

    @Aspect
    object $name {
        @Before(${targets.joinToString { "$it::class" }}, inherits = $inherits)
        fun log(joinPoint: JoinPoint) {
            shared.CallLog.calls += "$tag"${if (withMethodName) " + \":\" + joinPoint.signature.methodName" else ""}
        }
    }
    """.trimStart()

fun callLogTargetFile(className: String = "Target") = """
        class $className {
            @shared.LogCall
            fun run() {}
        }
""".trimIndent()

// open class <name> [: <parent>] { [@shared.LogCall] open|override fun run() { [super.run()] } }
fun callLogClassFile(
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

fun callLogWeavingTestFile(vararg statements: String) = """
    import org.junit.jupiter.api.Assertions.assertEquals
    import org.junit.jupiter.api.Test
    import shared.CallLog

    class WeavingTest {
        @Test
        fun `advices fire`() {
            CallLog.calls.clear()
            ${statements.joinToString("\n") { "        $it" }}
        }
    }
    """

// endregion
