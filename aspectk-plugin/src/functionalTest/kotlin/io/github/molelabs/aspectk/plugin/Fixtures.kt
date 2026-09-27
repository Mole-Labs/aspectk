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

fun aspectFile(postFix: String = "") = """
        import io.github.molelabs.aspectk.runtime.Aspect
        import io.github.molelabs.aspectk.runtime.Before
        import io.github.molelabs.aspectk.runtime.JoinPoint

        annotation class LogCall$postFix

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
