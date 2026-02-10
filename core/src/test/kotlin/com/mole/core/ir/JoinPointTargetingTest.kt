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
package com.mole.core.ir

import com.mole.core.compile
import com.mole.core.execute
import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class JoinPointTargetingTest {
    @Test
    fun `JoinPoint should be injected into extension function`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(2, joinPoint.args.size)
                        assertEquals(null, joinPoint.target)
                        assertIs<Test>(joinPoint.args[0])
                        assertEquals("extensionArg", joinPoint.args[1])
                    }
                }

                @TargetExample("example1")
                fun Test.extension(arg: String) {
                    assertEquals(true, ExampleAspect.executed)
                }

                class Test {
                    fun test1(arg:String) {
                        extension(arg)
                    }
                }

                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute("extensionArg")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `JoinPoint should be injected into suspend function`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                import kotlinx.coroutines.delay
                import kotlinx.coroutines.CoroutineScope
                import kotlinx.coroutines.launch
                import kotlinx.coroutines.delay

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        val scope = joinPoint.args[2] as CoroutineScope
                        scope.launch {
                            executed = true
                            assertEquals(3, joinPoint.args.size)
                            assertIs<Test>(joinPoint.target)
                            assertEquals("suspendArg", joinPoint.args[1])
                        }
                    }
                }

                class Test {
                    fun test1(arg:String, scope:CoroutineScope) {
                        scope.launch {
                            suspendFun(arg, scope)
                        }
                    }
                }

                @TargetExample("example1")
                suspend fun suspendFun(arg: String, scope:CoroutineScope) {
                    assertEquals(true, ExampleAspect.executed)
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        runTest {
            result.classLoader.execute("suspendArg", CoroutineScope(StandardTestDispatcher()))
            advanceUntilIdle()
        }
    }

    @Test
    fun `JoinPoint should be injected into top-level function`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertNull

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(1, joinPoint.args.size)
                        assertNull(joinPoint.target)
                        assertEquals("topLevelArg", joinPoint.args[0])
                    }
                }

                @TargetExample("example1")
                fun topLevelFunction(arg: String) {
                    assertEquals(ExampleAspect.executed, true)
                }

                class Test {
                    fun test1(arg:String) {
                        topLevelFunction(arg)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute("topLevelArg")
    }

    @Test
    fun `JoinPoint should be injected into getter`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs


                @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(1, joinPoint.args.size)
                        assertIs<Test>(joinPoint.args[0])
                    }
                }

                @TargetExample("example1")
                fun topLevelFunction(arg: String) {
                    assertEquals(true, ExampleAspect.executed)
                }

                class Test {
                    private val arg1:String
                        @TargetExample("example1")
                        get() = "hello"

                    fun test1() {
                        arg1
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute()
    }

    @Test
    fun `JoinPoint should be injected into setter`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs


                @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(2, joinPoint.args.size)
                        assertIs<Test>(joinPoint.args[0])
                        assertEquals("hello", joinPoint.args[1])
                    }
                }

                @TargetExample("example1")
                fun topLevelFunction(arg: String) {
                    assertEquals(true, ExampleAspect.executed)
                }


                class Test {
                    private var arg1:String = ""
                        @TargetExample("example1")
                        set(value) {}

                    fun test1() {
                        arg1 = "hello"
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute()
    }
}
