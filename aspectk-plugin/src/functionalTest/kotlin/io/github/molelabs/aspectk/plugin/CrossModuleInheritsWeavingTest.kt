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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// `inherits = true` weaving across class hierarchies, split across modules and, as a control, kept
// in a single module. Every class overrides `run()` without calling super unless stated, so each
// execution records "A" once; an override calling super.run() executes two woven bodies ("A", "A").
class CrossModuleInheritsWeavingTest {
    @TempDir
    lateinit var projectDir: File

    // base                 Base [@LogCall]
    // └─ child             Child : Base [override]
    //    └─ grandchild*    GrandChild : Child [override]
    @Test
    fun `inherits advice reaches every override down a cross-module chain`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "child" to listOf(callLogClassFile("Child", parent = "Base")),
                    "grandchild" to listOf(callLogClassFile("GrandChild", parent = "Child")),
                ),
                expectedCalls =
                listOf(
                    "Base().run()" to listOf("A"),
                    "Child().run()" to listOf("A"),
                    "GrandChild().run()" to listOf("A"),
                ),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // base           Base [@LogCall]
    // └─ child             Child : Base [@LogCall override]
    //    └─ grandchild*    GrandChild : Child [override]
    @Test
    fun `an annotated override under an annotated base in another module runs direct and inherited advice once each`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "child" to listOf(callLogClassFile("Child", parent = "Base", annotated = true)),
                    "grandchild" to listOf(callLogClassFile("GrandChild", parent = "Child")),
                ),
                expectedCalls =
                listOf(
                    "Child().run()" to listOf("A", "A"),
                    "GrandChild().run()" to listOf("A"),
                ),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // base                 Base [open]
    // └─ child             Child : Base [@LogCall override]
    //    └─ grandchild*    GrandChild : Child [override]
    @Test
    fun `inherits advice starts from an annotated middle class in another module`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base")),
                    "child" to listOf(callLogClassFile("Child", parent = "Base", annotated = true)),
                    "grandchild" to listOf(callLogClassFile("GrandChild", parent = "Child")),
                ),
                expectedCalls =
                listOf(
                    "Base().run()" to emptyList(),
                    "Child().run()" to listOf("A"),
                    "GrandChild().run()" to listOf("A"),
                ),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // base                 Base [@LogCall]
    // └─ child             Child : Base [override]
    //    └─ grandchild*    GrandChild : Child [no override]
    @Test
    fun `a class that does not override runs the inherited woven body from another module`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "child" to listOf(callLogClassFile("Child", parent = "Base")),
                    "grandchild" to listOf(callLogClassFile("GrandChild", parent = "Child", overridesRun = false)),
                ),
                expectedCalls = listOf("GrandChild().run()" to listOf("A")),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // base                 Base [@LogCall]
    // └─ child             Child : Base [override]
    //    └─ grandchild*    GrandChild : Child [override]
    // (AspectA declared with inherits = false)
    @Test
    fun `advice without inherits does not reach overrides in other modules`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "child" to listOf(callLogClassFile("Child", parent = "Base")),
                    "grandchild" to listOf(callLogClassFile("GrandChild", parent = "Child")),
                ),
                expectedCalls =
                listOf(
                    "Base().run()" to listOf("A"),
                    "Child().run()" to emptyList(),
                    "GrandChild().run()" to emptyList(),
                ),
                inherits = false,
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // base         Base [@LogCall]
    // └─ child*    Child : Base [override, calls super]
    @Test
    fun `an override calling super runs the advice for both executions across modules`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "child" to listOf(callLogClassFile("Child", parent = "Base", callsSuper = true)),
                ),
                expectedCalls = listOf("Child().run()" to listOf("A", "A")),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // app*    Base [@LogCall] <- Child [override] <- GrandChild [override]
    @Test
    fun `inherits advice reaches every override down a chain in the same module`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClassFile("Base", annotated = true),
                            callLogClassFile("Child", parent = "Base"),
                            callLogClassFile("GrandChild", parent = "Child"),
                        ),
                ),
                expectedCalls =
                listOf(
                    "Base().run()" to listOf("A"),
                    "Child().run()" to listOf("A"),
                    "GrandChild().run()" to listOf("A"),
                ),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // app*    Base [@LogCall] <- Child [@LogCall override] <- GrandChild [override]
    @Test
    fun `an annotated override under an annotated base in the same module runs direct and inherited advice once each`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClassFile("Base", annotated = true),
                            callLogClassFile("Child", parent = "Base", annotated = true),
                            callLogClassFile("GrandChild", parent = "Child"),
                        ),
                ),
                expectedCalls =
                listOf(
                    "Child().run()" to listOf("A", "A"),
                    "GrandChild().run()" to listOf("A"),
                ),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // app*    Base [@LogCall] <- Child [override, calls super]
    @Test
    fun `an override calling super runs the advice for both executions in the same module`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClassFile("Base", annotated = true),
                            callLogClassFile("Child", parent = "Base", callsSuper = true),
                        ),
                ),
                expectedCalls = listOf("Child().run()" to listOf("A", "A")),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // endregion

    // region tree: Base <- Child1 <- GrandChild1, Base <- Child2 <- GrandChild2 <- GrandGrandChild2

    // base                            Base [@LogCall]
    // └─ children                     Child1 : Base, Child2 : Base [override]
    //    └─ grandchildren             GrandChild1 : Child1, GrandChild2 : Child2 [override]
    //       └─ grandgrandchildren*    GrandGrandChild2 : GrandChild2 [override]
    @Test
    fun `inherits advice reaches every class of a tree split by depth across modules`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "children" to
                        listOf(
                            callLogClassFile("Child1", parent = "Base"),
                            callLogClassFile("Child2", parent = "Base"),
                        ),
                    "grandchildren" to
                        listOf(
                            callLogClassFile("GrandChild1", parent = "Child1"),
                            callLogClassFile("GrandChild2", parent = "Child2"),
                        ),
                    "grandgrandchildren" to listOf(callLogClassFile("GrandGrandChild2", parent = "GrandChild2")),
                ),
                expectedCalls = treeClasses.map { "$it().run()" to listOf("A") },
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // app*    Base [@LogCall] <- Child1, Child2 <- GrandChild1, GrandChild2 <- GrandGrandChild2 (all override)
    @Test
    fun `inherits advice reaches every class of a tree in the same module`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "app" to
                        listOf(
                            callLogClassFile("Base", annotated = true),
                            callLogClassFile("Child1", parent = "Base"),
                            callLogClassFile("Child2", parent = "Base"),
                            callLogClassFile("GrandChild1", parent = "Child1"),
                            callLogClassFile("GrandChild2", parent = "Child2"),
                            callLogClassFile("GrandGrandChild2", parent = "GrandChild2"),
                        ),
                ),
                expectedCalls = treeClasses.map { "$it().run()" to listOf("A") },
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // GrandChild2 inherits Child2.run as a fake override, so GrandGrandChild2's override has to be
    // traced through it back to the annotated Base.
    //
    // base                            Base [@LogCall]
    // └─ children                     Child1 : Base, Child2 : Base [override]
    //    └─ grandchildren             GrandChild1 : Child1 [override], GrandChild2 : Child2 [no override]
    //       └─ grandgrandchildren*    GrandGrandChild2 : GrandChild2 [override]
    @Test
    fun `a non-overriding middle class does not break inheritance down a tree across modules`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to listOf(callLogClassFile("Base", annotated = true)),
                    "children" to
                        listOf(
                            callLogClassFile("Child1", parent = "Base"),
                            callLogClassFile("Child2", parent = "Base"),
                        ),
                    "grandchildren" to
                        listOf(
                            callLogClassFile("GrandChild1", parent = "Child1"),
                            callLogClassFile("GrandChild2", parent = "Child2", overridesRun = false),
                        ),
                    "grandgrandchildren" to listOf(callLogClassFile("GrandGrandChild2", parent = "GrandChild2")),
                ),
                expectedCalls =
                listOf(
                    "GrandChild2().run()" to listOf("A"),
                    "GrandGrandChild2().run()" to listOf("A"),
                ),
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // The most common real-world shape: the annotated function is an abstract interface member,
    // so only implementations have bodies to weave.
    //
    // base                            interface Base [@LogCall abstract]
    // └─ children                     Child1 : Base, Child2 : Base [override]
    //    └─ grandchildren             GrandChild1 : Child1, GrandChild2 : Child2 [override]
    //       └─ grandgrandchildren*    GrandGrandChild2 : GrandChild2 [override]
    @Test
    fun `inherits advice from an annotated interface function reaches every implementation across modules`() {
        // given
        val testTask =
            writeInheritanceProject(
                modules =
                listOf(
                    "base" to
                        listOf(
                            """
                            interface Base {
                                @shared.LogCall
                                fun run()
                            }
                            """.trimIndent(),
                        ),
                    "children" to
                        listOf(
                            callLogClassFile("Child1", parent = "Base", parentIsInterface = true),
                            callLogClassFile("Child2", parent = "Base", parentIsInterface = true),
                        ),
                    "grandchildren" to
                        listOf(
                            callLogClassFile("GrandChild1", parent = "Child1"),
                            callLogClassFile("GrandChild2", parent = "Child2"),
                        ),
                    "grandgrandchildren" to listOf(callLogClassFile("GrandGrandChild2", parent = "GrandChild2")),
                ),
                expectedCalls = (treeClasses - "Base").map { "$it().run()" to listOf("A") },
            )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, testTask)
    }

    // endregion

    private val treeClasses = listOf("Base", "Child1", "Child2", "GrandChild1", "GrandChild2", "GrandGrandChild2")

    /**
     * Writes `shared <- aspect <- modules[0] <- modules[1] <- ...`, each module api-depending on the
     * previous one, with all of a module's classes in one file and an `inherits`-configurable
     * CallLog aspect on `@shared.LogCall`. The weaving test goes into the last module: for each
     * (call, calls) it clears the log, makes the call and asserts exactly those tags were recorded.
     *
     * @return the test task path of the last module
     */
    private fun writeInheritanceProject(
        modules: List<Pair<String, List<String>>>,
        expectedCalls: List<Pair<String, List<String>>>,
        inherits: Boolean = true,
    ): String {
        val dependencies =
            modules.mapIndexed { index, (name, _) ->
                val upstream = if (index == 0) "aspect" else modules[index - 1].first
                name to "api(project(\":$upstream\"))"
            }
        writeProject(
            projectDir,
            "shared" to "",
            "aspect" to """api(project(":shared"))""",
            *dependencies.toTypedArray(),
        )
        writeFile(projectDir, "shared/src/main/kotlin/Shared.kt", sharedModuleFile())
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A", inherits = inherits),
        )
        modules.forEach { (name, classes) ->
            writeFile(
                projectDir,
                "$name/src/main/kotlin/${name.replaceFirstChar { it.uppercase() }}Classes.kt",
                classes.joinToString("\n"),
            )
        }

        val testModule = modules.last().first
        val statements =
            expectedCalls.flatMap { (call, calls) ->
                listOf(
                    "CallLog.calls.clear()",
                    call,
                    "assertEquals(listOf<String>(${calls.joinToString { "\"$it\"" }}), CallLog.calls, \"$call\")",
                )
            }
        writeFile(
            projectDir,
            "$testModule/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(*statements.toTypedArray()),
        )
        return ":$testModule:test"
    }

    private fun assertTestsPass(
        result: BuildResult,
        vararg testTasks: String,
    ) {
        testTasks.forEach {
            assertEquals(
                TaskOutcome.SUCCESS,
                result.task(it)?.outcome,
                result.output,
            )
        }
    }
}
