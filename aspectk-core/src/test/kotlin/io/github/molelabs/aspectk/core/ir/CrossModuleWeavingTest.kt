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
import io.github.molelabs.aspectk.core.AdviceKind
import io.github.molelabs.aspectk.core.compileModules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@Suppress("UNCHECKED_CAST")
class CrossModuleWeavingTest {
    // shared      LogCall, CallLog
    // aspect-a    AspectA -> "A"
    // aspect-b    AspectB -> "B"
    // feature     Target [@LogCall]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `aspects from two modules both weave into the same target`(kind: AdviceKind) {
        // given
        val loader =
            compileModules(
                sharedModule,
                "aspect-a" to listOf(
                    SourceFile.kotlin(
                        "AspectA.kt",
                        kind.aspect(
                            "AspectA",
                            "shared.LogCall::class",
                            "shared.CallLog.calls += \"A\"",
                        ),
                    ),
                ),
                "aspect-b" to listOf(
                    SourceFile.kotlin(
                        "AspectB.kt",
                        kind.aspect(
                            "AspectB",
                            "shared.LogCall::class",
                            "shared.CallLog.calls += \"B\"",
                        ),
                    ),
                ),
                "feature" to listOf(
                    SourceFile.kotlin("Target.kt", callLogTarget()),
                    runnerFile("Target().run()"),
                ),
            )

        // when
        val calls = loader.runTest() as List<List<String>>

        // then: how two advices of other modules nest isn't settled, so only membership is pinned
        assertEquals(listOf("A", "B"), calls.single().sorted())
    }

    // shared     LogCall, CallLog
    // aspect     ExternalAspect -> "external"
    // feature    LocalAspect -> "local", Target [@LogCall]
    @Test
    fun `local advice runs before external advice on the same target`() {
        // given
        val kind = AdviceKind.BEFORE
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "ExternalAspect.kt",
                            kind.aspect(
                                "ExternalAspect",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"external\"",
                            ),
                        ),
                    ),
                "feature" to
                    listOf(
                        SourceFile.kotlin(
                            "LocalAspect.kt",
                            kind.aspect(
                                "LocalAspect",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"local\"",
                            ),
                        ),
                        SourceFile.kotlin("Target.kt", callLogTarget()),
                        runnerFile("Target().run()"),
                    ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("local", "external")), calls)
    }

    // shared      LogCall, CallLog
    // aspect-a    a.LoggingAspect.advice -> "a"
    // aspect-b    b.LoggingAspect.advice -> "b"
    // feature     Target [@LogCall]
    @Test
    fun `same aspect and advice names in different packages stay separate`() {
        // given
        val kind = AdviceKind.BEFORE
        val loader =
            compileModules(
                sharedModule,
                "aspect-a" to
                    listOf(
                        SourceFile.kotlin(
                            "LoggingAspect.kt",
                            "package a\n" + kind.aspect(
                                "LoggingAspect",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"a\"",
                            ),
                        ),
                    ),
                "aspect-b" to
                    listOf(
                        SourceFile.kotlin(
                            "LoggingAspect.kt",
                            "package b\n" + kind.aspect(
                                "LoggingAspect",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"b\"",
                            ),
                        ),
                    ),
                "feature" to listOf(
                    SourceFile.kotlin("Target.kt", callLogTarget()),
                    runnerFile("Target().run()"),
                ),
            )

        // when
        val calls = loader.runTest() as List<List<String>>

        // then
        assertEquals(listOf("a", "b"), calls.single().sorted())
    }

    // shared     LogCall, CallLog
    // aspect     AspectA -> "A", AspectModuleTarget [@LogCall]
    // feature    Target [@LogCall]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `the aspect module weaves its own targets and its dependents' targets`(kind: AdviceKind) {
        // given
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "AspectA.kt",
                            kind.aspect(
                                "AspectA",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"A\"",
                            ),
                        ),
                        SourceFile.kotlin(
                            "AspectModuleTarget.kt",
                            callLogTarget("AspectModuleTarget"),
                        ),
                    ),
                "feature" to
                    listOf(
                        SourceFile.kotlin("Target.kt", callLogTarget()),
                        runnerFile("AspectModuleTarget().run()", "Target().run()"),
                    ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A"), listOf("A")), calls)
    }

    // shared     LogCall, CallLog
    // mod1       m1.First
    // mod2       m2.Second
    // aspect     AspectA on [First, Second] -> "A"
    // feature    Target.first [@First], Target.second [@Second]
    @Test
    fun `one advice targets annotations declared in two different modules`() {
        // given
        val kind = AdviceKind.BEFORE
        val loader =
            compileModules(
                sharedModule,
                "mod1" to listOf(
                    SourceFile.kotlin(
                        "First.kt",
                        "package m1\n\nannotation class First\n",
                    ),
                ),
                "mod2" to listOf(
                    SourceFile.kotlin(
                        "Second.kt",
                        "package m2\n\nannotation class Second\n",
                    ),
                ),
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "AspectA.kt",
                            kind.aspect(
                                "AspectA",
                                "m1.First::class, m2.Second::class",
                                "shared.CallLog.calls += \"A\"",
                            ),
                        ),
                    ),
                "feature" to
                    listOf(
                        SourceFile.kotlin(
                            "Target.kt",
                            """
                            class Target {
                                @m1.First
                                fun first() {}

                                @m2.Second
                                fun second() {}
                            }
                            """.trimIndent(),
                        ),
                        runnerFile("Target().first()", "Target().second()"),
                    ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A"), listOf("A")), calls)
    }

    // Top-level targets put `$MethodSignatures$<module>$<file>` into the file's package; without the
    // module in the name, two modules sharing a package and a file name emit the same class name
    // onto one classpath. JvmName keeps Kotlin's own file facades distinct so only the
    // plugin-generated class can collide; both modules number their signature fields from
    // ajc$tjp_0, so a collision silently hands runB runA's MethodSignature instead of crashing --
    // hence asserting on methodName.
    //
    // shared       LogCall, CallLog
    // aspect       AspectA -> "A:<methodName>"
    // feature-a    Targets.kt: runA [@LogCall]
    // feature-b    Targets.kt: runB [@LogCall]
    // app
    @Test
    fun `two modules with same-named files of top-level targets weave independently`() {
        // given
        val kind = AdviceKind.BEFORE
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "AspectA.kt",
                            kind.aspect(
                                "AspectA",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"A:\" + joinPoint.signature.methodName",
                            ),
                        ),
                    ),
                "feature-a" to listOf(
                    SourceFile.kotlin(
                        "Targets.kt",
                        "@file:JvmName(\"FeatureA\")\n\n@shared.LogCall\nfun runA() {}\n",
                    ),
                ),
                "feature-b" to listOf(
                    SourceFile.kotlin(
                        "Targets.kt",
                        "@file:JvmName(\"FeatureB\")\n\n@shared.LogCall\nfun runB(value: Int) {}\n",
                    ),
                ),
                "app" to listOf(runnerFile("runA()", "runB(1)")),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A:runA"), listOf("A:runB")), calls)
    }

    // shared     LogCall, CallLog
    // base       Base.run [@LogCall]
    // aspect     AspectA (inherits) -> "A"
    // feature    Child : Base [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice weaves into an override of a function declared in another module`(kind: AdviceKind) {
        // given
        val loader =
            compileModules(
                sharedModule,
                "base" to listOf(
                    SourceFile.kotlin(
                        "Base.kt",
                        callLogClass("Base", annotated = true),
                    ),
                ),
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "AspectA.kt",
                            kind.aspect(
                                "AspectA",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"A\"",
                                inherits = true,
                            ),
                        ),
                    ),
                "feature" to listOf(
                    SourceFile.kotlin(
                        "Child.kt",
                        callLogClass("Child", parent = "Base"),
                    ),
                    runnerFile("Child().run()"),
                ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A")), calls)
    }

    // The target is woven where it is declared but only runs inlined into the other module, where
    // its reified type argument is known; @Around's proceed listener is regenerated there too.
    //
    // shared    LogCall, CallLog
    // lib       AspectA -> "A", typeName<reified T>() [@LogCall inline]
    // app       typeName<String>()
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `a reified inline target from another module weaves where it is inlined`(kind: AdviceKind) {
        // given
        val loader =
            compileModules(
                sharedModule,
                "lib" to
                    listOf(
                        SourceFile.kotlin(
                            "AspectA.kt",
                            kind.aspect(
                                "AspectA",
                                "shared.LogCall::class",
                                "shared.CallLog.calls += \"A\"",
                            ),
                        ),
                        SourceFile.kotlin(
                            "TypeName.kt",
                            "@shared.LogCall\ninline fun <reified T> typeName(): String = T::class.simpleName!!\n",
                        ),
                    ),
                "app" to listOf(runnerFile("shared.CallLog.calls += typeName<String>()")),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A", "String")), calls)
    }

    // shared     LogCall, CallLog
    // aspect     ReplacingAspect: proceed("replaced")
    // feature    Target.echo [@LogCall]
    @Test
    fun `@Around replaces the arguments of a target in another module through proceed`() {
        // given
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "ReplacingAspect.kt",
                            """
                            import io.github.molelabs.aspectk.runtime.Around
                            import io.github.molelabs.aspectk.runtime.Aspect
                            import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                            @Aspect
                            object ReplacingAspect {
                                @Around(shared.LogCall::class)
                                fun advice(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed("replaced")
                            }
                            """.trimIndent(),
                        ),
                    ),
                "feature" to
                    listOf(
                        SourceFile.kotlin(
                            "Target.kt",
                            "class Target {\n    @shared.LogCall\n    fun echo(value: String): String = value\n}\n",
                        ),
                        runnerFile("shared.CallLog.calls += Target().echo(\"original\")"),
                    ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("replaced")), calls)
    }

    // shared     LogCall, CallLog
    // aspect     SuspendAspect -> "A" (suspend @Around)
    // feature    Target.run [@LogCall suspend]
    @Test
    fun `a suspend @Around from another module weaves into a suspend target`() {
        // given
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "SuspendAspect.kt",
                            """
                            import io.github.molelabs.aspectk.runtime.Around
                            import io.github.molelabs.aspectk.runtime.Aspect
                            import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint

                            @Aspect
                            object SuspendAspect {
                                @Around(shared.LogCall::class)
                                suspend fun advice(joinPoint: SuspendProceedingJoinPoint): Any? {
                                    shared.CallLog.calls += "A"
                                    return joinPoint.proceed()
                                }
                            }
                            """.trimIndent(),
                        ),
                    ),
                "feature" to
                    listOf(
                        SourceFile.kotlin(
                            "Target.kt",
                            """
                            class Target {
                                @shared.LogCall
                                suspend fun run(): Int {
                                    kotlinx.coroutines.yield()
                                    return 7
                                }
                            }
                            """.trimIndent(),
                        ),
                        runnerFile("shared.CallLog.calls += kotlinx.coroutines.runBlocking { Target().run() }.toString()"),
                    ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A", "7")), calls)
    }

    // On the JVM an internal object is public in bytecode, and an internal member only gets a
    // mangled name, so the woven call from another module resolves it
    //
    // shared     LogCall, CallLog
    // aspect     internal InternalAspect.advice (internal) -> "A"
    // feature    Target [@LogCall]
    @Test
    fun `an internal aspect with an internal advice still weaves into another module`() {
        // given
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "InternalAspect.kt",
                            """
                            import io.github.molelabs.aspectk.runtime.Aspect
                            import io.github.molelabs.aspectk.runtime.Before
                            import io.github.molelabs.aspectk.runtime.JoinPoint

                            @Aspect
                            internal object InternalAspect {
                                @Before(shared.LogCall::class)
                                internal fun advice(joinPoint: JoinPoint) {
                                    shared.CallLog.calls += "A"
                                }
                            }
                            """.trimIndent(),
                        ),
                    ),
                "feature" to listOf(
                    SourceFile.kotlin("Target.kt", callLogTarget()),
                    runnerFile("Target().run()"),
                ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A")), calls)
    }
}
