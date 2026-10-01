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

// `inherits = true` weaving across class hierarchies, split across modules and, as a control, kept
// in a single module. Every class overrides `run()` without calling super unless stated, so each
// execution records "A" once; an override calling super.run() executes two woven bodies ("A", "A").
// Graphs list the modules compiled after `shared` and `aspect`; each module sees every one above it.
class CrossModuleInheritsWeavingTest {
    // base            Base [@LogCall]
    // └─ child        Child : Base [override]
    //    └─ grandchild    GrandChild : Child [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice reaches every override down a cross-module chain`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "Base().run()" to listOf("A"),
                "Child().run()" to listOf("A"),
                "GrandChild().run()" to listOf("A"),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "child" to listOf(callLogClass("Child", parent = "Base")),
                    "grandchild" to listOf(callLogClass("GrandChild", parent = "Child")),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // base            Base [@LogCall]
    // └─ child        Child : Base [@LogCall override]
    //    └─ grandchild    GrandChild : Child [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `an annotated override under an annotated base in another module runs direct and inherited advice once each`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "Child().run()" to listOf("A", "A"),
                "GrandChild().run()" to listOf("A"),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "child" to listOf(callLogClass("Child", parent = "Base", annotated = true)),
                    "grandchild" to listOf(callLogClass("GrandChild", parent = "Child")),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // base            Base [open]
    // └─ child        Child : Base [@LogCall override]
    //    └─ grandchild    GrandChild : Child [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice starts from an annotated middle class in another module`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "Base().run()" to emptyList(),
                "Child().run()" to listOf("A"),
                "GrandChild().run()" to listOf("A"),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base")),
                    "child" to listOf(callLogClass("Child", parent = "Base", annotated = true)),
                    "grandchild" to listOf(callLogClass("GrandChild", parent = "Child")),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // base            Base [@LogCall]
    // └─ child        Child : Base [override]
    //    └─ grandchild    GrandChild : Child [no override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `a class that does not override runs the inherited woven body from another module`(kind: AdviceKind) {
        // given
        val expectedCalls = listOf("GrandChild().run()" to listOf("A"))
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "child" to listOf(callLogClass("Child", parent = "Base")),
                    "grandchild" to listOf(callLogClass("GrandChild", parent = "Child", overridesRun = false)),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // base            Base [@LogCall]
    // └─ child        Child : Base [override]
    //    └─ grandchild    GrandChild : Child [override]
    // (AspectA declared with inherits = false)
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `advice without inherits does not reach overrides in other modules`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "Base().run()" to listOf("A"),
                "Child().run()" to emptyList(),
                "GrandChild().run()" to emptyList(),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "child" to listOf(callLogClass("Child", parent = "Base")),
                    "grandchild" to listOf(callLogClass("GrandChild", parent = "Child")),
                ),
                calls = expectedCalls.map { it.first },
                inherits = false,
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // With @Around the override's body, super call included, moves into the proceed listener. Kotlin
    // compiles that lambda to a static method of Child, where invokespecial Base.run is allowed.
    //
    // base        Base [@LogCall]
    // └─ child    Child : Base [override, calls super]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `an override calling super runs the advice for both executions across modules`(kind: AdviceKind) {
        // given
        val expectedCalls = listOf("Child().run()" to listOf("A", "A"))
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "child" to listOf(callLogClass("Child", parent = "Base", callsSuper = true)),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // A suspend listener compiles to its own continuation class, which can't use invokespecial on
    // Child's super method, so the JVM backend routes the super call through a synthetic accessor
    // (`access$run$s<hash>`) on Child. That only happens while the moved body's parents point at
    // the listener.
    //
    // base        Base [@LogCall suspend]
    // └─ child    Child : Base [override suspend, calls super]
    @Test
    fun `a suspend override calling super runs @Around for both executions across modules`() {
        // given
        val loader =
            compileModules(
                sharedModule,
                "aspect" to
                    listOf(
                        SourceFile.kotlin(
                            "AspectA.kt",
                            """
                            import io.github.molelabs.aspectk.runtime.Around
                            import io.github.molelabs.aspectk.runtime.Aspect
                            import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint

                            @Aspect
                            object AspectA {
                                @Around(shared.LogCall::class, inherits = true)
                                suspend fun advice(joinPoint: SuspendProceedingJoinPoint): Any? {
                                    shared.CallLog.calls += "A"
                                    return joinPoint.proceed()
                                }
                            }
                            """.trimIndent(),
                        ),
                    ),
                "base" to listOf(SourceFile.kotlin("Base.kt", "open class Base {\n    @shared.LogCall\n    open suspend fun run() {}\n}\n")),
                "child" to
                    listOf(
                        SourceFile.kotlin("Child.kt", "class Child : Base() {\n    override suspend fun run() { super.run() }\n}\n"),
                        runnerFile("kotlinx.coroutines.runBlocking { Child().run() }"),
                    ),
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(listOf(listOf("A", "A")), calls)
    }

    // The override in another module sees Base<T>.run as a lazy IR declaration whose overridden
    // chain has to be walked without assuming in-module IR types
    //
    // base        Base<T> [@LogCall run(value: T)]
    // └─ child    Child : Base<String> [override run(value: String)]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice reaches an override of a generic function from another module`(kind: AdviceKind) {
        // given
        val expectedCalls = listOf("Child().run(\"s\")" to listOf("A"))
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf("open class Base<T> {\n    @shared.LogCall\n    open fun run(value: T) {}\n}\n"),
                    "child" to listOf("class Child : Base<String>() {\n    override fun run(value: String) {}\n}\n"),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // app    Base [@LogCall] <- Child [override] <- GrandChild [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice reaches every override down a chain in the same module`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "Base().run()" to listOf("A"),
                "Child().run()" to listOf("A"),
                "GrandChild().run()" to listOf("A"),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClass("Base", annotated = true),
                            callLogClass("Child", parent = "Base"),
                            callLogClass("GrandChild", parent = "Child"),
                        ),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // app    Base [@LogCall] <- Child [@LogCall override] <- GrandChild [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `an annotated override under an annotated base in the same module runs direct and inherited advice once each`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "Child().run()" to listOf("A", "A"),
                "GrandChild().run()" to listOf("A"),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClass("Base", annotated = true),
                            callLogClass("Child", parent = "Base", annotated = true),
                            callLogClass("GrandChild", parent = "Child"),
                        ),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // app    Base [@LogCall] <- Child [override, calls super]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `an override calling super runs the advice for both executions in the same module`(kind: AdviceKind) {
        // given
        val expectedCalls = listOf("Child().run()" to listOf("A", "A"))
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClass("Base", annotated = true),
                            callLogClass("Child", parent = "Base", callsSuper = true),
                        ),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // region tree: Base <- Child1 <- GrandChild1, Base <- Child2 <- GrandChild2 <- GrandGrandChild2

    // base                   Base [@LogCall]
    // └─ children            Child1 : Base, Child2 : Base [override]
    //    └─ grandchildren    GrandChild1 : Child1, GrandChild2 : Child2 [override]
    //       └─ grandgrandchildren    GrandGrandChild2 : GrandChild2 [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice reaches every class of a tree split by depth across modules`(kind: AdviceKind) {
        // given
        val expectedCalls = treeClasses.map { "$it().run()" to listOf("A") }
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "children" to listOf(callLogClass("Child1", parent = "Base"), callLogClass("Child2", parent = "Base")),
                    "grandchildren" to
                        listOf(callLogClass("GrandChild1", parent = "Child1"), callLogClass("GrandChild2", parent = "Child2")),
                    "grandgrandchildren" to listOf(callLogClass("GrandGrandChild2", parent = "GrandChild2")),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // app    Base [@LogCall] <- Child1, Child2 <- GrandChild1, GrandChild2 <- GrandGrandChild2 (all override)
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice reaches every class of a tree in the same module`(kind: AdviceKind) {
        // given
        val expectedCalls = treeClasses.map { "$it().run()" to listOf("A") }
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClass("Base", annotated = true),
                            callLogClass("Child1", parent = "Base"),
                            callLogClass("Child2", parent = "Base"),
                            callLogClass("GrandChild1", parent = "Child1"),
                            callLogClass("GrandChild2", parent = "Child2"),
                            callLogClass("GrandGrandChild2", parent = "GrandChild2"),
                        ),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // GrandChild2 inherits Child2.run as a fake override, so GrandGrandChild2's override has to be
    // traced through it back to the annotated Base.
    //
    // base                   Base [@LogCall]
    // └─ children            Child1 : Base, Child2 : Base [override]
    //    └─ grandchildren    GrandChild1 : Child1 [override], GrandChild2 : Child2 [no override]
    //       └─ grandgrandchildren    GrandGrandChild2 : GrandChild2 [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `a non-overriding middle class does not break inheritance down a tree across modules`(kind: AdviceKind) {
        // given
        val expectedCalls =
            listOf(
                "GrandChild2().run()" to listOf("A"),
                "GrandGrandChild2().run()" to listOf("A"),
            )
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf(callLogClass("Base", annotated = true)),
                    "children" to listOf(callLogClass("Child1", parent = "Base"), callLogClass("Child2", parent = "Base")),
                    "grandchildren" to
                        listOf(
                            callLogClass("GrandChild1", parent = "Child1"),
                            callLogClass("GrandChild2", parent = "Child2", overridesRun = false),
                        ),
                    "grandgrandchildren" to listOf(callLogClass("GrandGrandChild2", parent = "GrandChild2")),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // The most common real-world shape: the annotated function is an abstract interface member,
    // so only implementations have bodies to weave.
    //
    // base                   interface Base [@LogCall abstract]
    // └─ children            Child1 : Base, Child2 : Base [override]
    //    └─ grandchildren    GrandChild1 : Child1, GrandChild2 : Child2 [override]
    //       └─ grandgrandchildren    GrandGrandChild2 : GrandChild2 [override]
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(AdviceKind::class)
    fun `inherits advice from an annotated interface function reaches every implementation across modules`(kind: AdviceKind) {
        // given
        val expectedCalls = (treeClasses - "Base").map { "$it().run()" to listOf("A") }
        val loader =
            compileHierarchy(
                kind,
                modules =
                listOf(
                    "base" to listOf("interface Base {\n    @shared.LogCall\n    fun run()\n}\n"),
                    "children" to
                        listOf(
                            callLogClass("Child1", parent = "Base", parentIsInterface = true),
                            callLogClass("Child2", parent = "Base", parentIsInterface = true),
                        ),
                    "grandchildren" to
                        listOf(callLogClass("GrandChild1", parent = "Child1"), callLogClass("GrandChild2", parent = "Child2")),
                    "grandgrandchildren" to listOf(callLogClass("GrandGrandChild2", parent = "GrandChild2")),
                ),
                calls = expectedCalls.map { it.first },
            )

        // when
        val calls = loader.runTest()

        // then
        assertEquals(expectedCalls.map { it.second }, calls)
    }

    // endregion

    private val treeClasses = listOf("Base", "Child1", "Child2", "GrandChild1", "GrandChild2", "GrandGrandChild2")

    /**
     * Compiles `shared`, then `aspect` with an [inherits]-configurable [kind] advice on
     * `@shared.LogCall` that records "A", then [modules] in order with all of a module's classes in
     * one file. The last module also gets a runner making [calls].
     */
    private fun compileHierarchy(
        kind: AdviceKind,
        modules: List<Pair<String, List<String>>>,
        calls: List<String>,
        inherits: Boolean = true,
    ): ClassLoader {
        val aspect =
            "aspect" to
                listOf(
                    SourceFile.kotlin(
                        "AspectA.kt",
                        kind.aspect("AspectA", "shared.LogCall::class", "shared.CallLog.calls += \"A\"", inherits = inherits),
                    ),
                )
        val compiled =
            modules.mapIndexed { index, (name, classes) ->
                val sources = listOf(SourceFile.kotlin("Classes.kt", classes.joinToString("\n")))
                name to if (index == modules.lastIndex) sources + runnerFile(*calls.toTypedArray()) else sources
            }
        return compileModules(sharedModule, aspect, *compiled.toTypedArray())
    }
}
