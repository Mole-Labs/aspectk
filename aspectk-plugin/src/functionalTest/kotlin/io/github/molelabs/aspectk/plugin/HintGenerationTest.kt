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

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// hints.json = localHints + carriedForwardHints; externalHints (hintsPath) are consumed but never
// re-emitted. Sections: localHints, carry-forward on incremental rounds, consuming external hints,
// and hint propagation across a module chain.
// Graphs above cross-module tests list every module as `name  <- dependencies  contents`; `*` marks
// the module whose hints or weaving the test checks and `x -> y` a change made in the when step.
class HintGenerationTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `hints json records every field of a local advice`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/Aspect.kt", aspectFile())

        // when
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        assertEquals(
            """[{"package":"","class":"LoggingAspect","function":"log","kind":"BEFORE","targets":["LogCall"],"inherits":false}]""",
            hintsOf(projectDir, "app"),
        )
    }

    @Test
    fun `a module without any aspect writes an empty hints json`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/Plain.kt", "class Plain\n")

        // when
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        assertEquals("[]", hintsOf(projectDir, "app"))
    }

    @Test
    fun `one record per advice with every target in declaration order`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/MixedAspect.kt", mixedAspectFile())

        // when
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        assertEquals(
            "[" +
                """{"package":"com.example.aspect","class":"MixedAspect","function":"before","kind":"BEFORE","targets":["com.example.aspect.First","com.example.aspect.Second"],"inherits":false},""" +
                """{"package":"com.example.aspect","class":"MixedAspect","function":"after","kind":"AFTER","targets":["com.example.aspect.First"],"inherits":true}""" +
                "]",
            hintsOf(projectDir, "app"),
        )
    }

    // a                   AspectA
    // b*    <- a (api)    AspectB
    @Test
    fun `a module does not re-emit the hints it consumed from its dependencies`() {
        // given
        writeProject(
            projectDir,
            "a" to "",
            "b" to """api(project(":a"))""",
        )
        writeFile(projectDir, "a/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "b/src/main/kotlin/AspectB.kt", aspectFile("B"))

        // when
        runGradle(projectDir, testKitDir(), "compileKotlin", "dumpHintsPath")

        // then: b did receive a's hint, so its absence from b's own hints.json means "not re-emitted"
        val receivedByB = hintsReceivedBy("b")
        assertTrue(receivedByB.getValue("a").hasAspect("LoggingAspectA"), receivedByB.toString())
        val hints = hintsOf(projectDir, "b")
        assertTrue(hints.hasAspect("LoggingAspectB"), hints)
        assertFalse(hints.hasAspect("LoggingAspectA"), hints)
    }

    @Test
    fun `test source set aspects go to the test compilation's hints only`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "app/src/test/kotlin/TestAspect.kt", aspectFile("T"))

        // when
        runGradle(projectDir, testKitDir(), "compileTestKotlin")

        // then
        val mainHints = hintsOf(projectDir, "app", "main")
        val testHints = hintsOf(projectDir, "app", "test")
        assertTrue(mainHints.hasAspect("LoggingAspect"), mainHints)
        assertFalse(mainHints.hasAspect("LoggingAspectT"), mainHints)
        assertTrue(testHints.hasAspect("LoggingAspectT"), testHints)
        assertFalse(testHints.hasAspect("LoggingAspect"), testHints)
    }

    @Test
    fun `hints json after an incremental round has the same records as the clean build`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "app/src/main/kotlin/AspectB.kt", aspectDependingOnSharedFile("B"))
        writeFile(
            projectDir,
            "app/src/main/kotlin/Shared.kt",
            "object Shared { val tag = \"v1\" }\n",
        )
        runGradle(projectDir, testKitDir(), "compileKotlin")
        val cleanHints = hintsOf(projectDir, "app")
        assertTrue(cleanHints.hasAspect("LoggingAspectA") && cleanHints.hasAspect("LoggingAspectB"), cleanHints)

        // when: Shared.kt has no aspect marker (stays incremental), but changing `tag`'s type
        // makes IC recompile AspectB.kt -> AspectB is visited, AspectA is carried forward
        writeFile(
            projectDir,
            "app/src/main/kotlin/Shared.kt",
            "object Shared { val tag: CharSequence = \"v1\" }\n",
        )
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        assertEquals(cleanHints.records(), hintsOf(projectDir, "app").records())
    }

    @Test
    fun `editing an unrelated file keeps every aspect in hints json`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "app/src/main/kotlin/AspectB.kt", aspectFile("B"))
        writeFile(projectDir, "app/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // when
        writeFile(projectDir, "app/src/main/kotlin/Unrelated.kt", "val unrelated = 2\n")
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        val hints = hintsOf(projectDir, "app")
        assertTrue(hints.hasAspect("LoggingAspectA"), hints)
        assertTrue(hints.hasAspect("LoggingAspectB"), hints)
    }

    @Test
    fun `deleting an aspect file removes its hint`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/AspectA.kt", aspectFile("A"))
        // LogCallB lives apart from AspectB so Target still compiles once AspectB.kt is gone
        writeFile(projectDir, "app/src/main/kotlin/LogCallB.kt", "annotation class LogCallB\n")
        writeFile(projectDir, "app/src/main/kotlin/AspectB.kt", aspectFile("B", declaresAnnotation = false))
        writeFile(projectDir, "app/src/main/kotlin/Target.kt", targetFile("B"))
        writeFile(projectDir, "app/src/test/kotlin/WeavingTest.kt", weavingTestFile(executionCount = 1, postFix = "B"))
        runGradle(projectDir, testKitDir(), "test")
        val hintsBefore = hintsOf(projectDir, "app")
        assertTrue(hintsBefore.hasAspect("LoggingAspectB"), hintsBefore)

        // when: a removed file is never marker-checked, so this round stays incremental; Target.kt
        // is untouched and does not reference LoggingAspectB in source, so IC won't recompile it
        removeFile(projectDir, "app/src/main/kotlin/AspectB.kt")
        removeFile(projectDir, "app/src/test/kotlin/WeavingTest.kt")
        writeFile(projectDir, "app/src/test/kotlin/TargetRunsTest.kt", targetRunsTestFile("B"))
        val result = runGradle(projectDir, testKitDir(), "test")

        // then: the hint is gone and Target no longer calls the deleted aspect
        val hints = hintsOf(projectDir, "app")
        assertTrue(hints.hasAspect("LoggingAspectA"), hints)
        assertFalse(hints.hasAspect("LoggingAspectB"), hints)
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:test")?.outcome, result.output)
    }

    @Test
    fun `deleting an unrelated file keeps the round incremental`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "app/src/main/kotlin/Target.kt", targetFile("A"))
        writeFile(projectDir, "app/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // when
        removeFile(projectDir, "app/src/main/kotlin/Unrelated.kt")
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then: no aspect disappeared, so detect must not force a full recompile
        assertEquals("false", aspectChangeResult("app"))
        val hints = hintsOf(projectDir, "app")
        assertTrue(hints.hasAspect("LoggingAspectA"), hints)
    }

    @Test
    fun `deleting an aspect together with its targets keeps the round incremental`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "app/src/main/kotlin/LogCallB.kt", "annotation class LogCallB\n")
        writeFile(projectDir, "app/src/main/kotlin/AspectB.kt", aspectFile("B", declaresAnnotation = false))
        writeFile(projectDir, "app/src/main/kotlin/TargetB.kt", targetFile("B"))
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // when: nothing left uses @LogCallB, so no stale woven call can survive
        removeFile(projectDir, "app/src/main/kotlin/AspectB.kt")
        removeFile(projectDir, "app/src/main/kotlin/TargetB.kt")
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        assertEquals("false", aspectChangeResult("app"))
        val hints = hintsOf(projectDir, "app")
        assertTrue(hints.hasAspect("LoggingAspectA"), hints)
        assertFalse(hints.hasAspect("LoggingAspectB"), hints)
    }

    @Test
    fun `removing only the @Aspect annotation stops the hint and the weaving`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "app/src/main/kotlin/Target.kt", targetFile())
        writeFile(
            projectDir,
            "app/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 1),
        )
        runGradle(projectDir, testKitDir(), "test")
        val hintsBefore = hintsOf(projectDir, "app")
        assertTrue(hintsBefore.hasAspect("LoggingAspect"), hintsBefore)

        // when: @Before is still there -> full rebuild, but the class is no longer an @Aspect
        writeFile(
            projectDir,
            "app/src/main/kotlin/Aspect.kt",
            aspectFile().replace("@Aspect\n", ""),
        )
        writeFile(
            projectDir,
            "app/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 0),
        )
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        val hints = hintsOf(projectDir, "app")
        assertFalse(hints.hasAspect("LoggingAspect"), hints)
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:test")?.outcome, result.output)
    }

    @Test
    fun `removing every aspect annotation stops the hint and the weaving`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "app/src/main/kotlin/Target.kt", targetFile())
        writeFile(
            projectDir,
            "app/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 1),
        )
        runGradle(projectDir, testKitDir(), "test")
        val hintsBefore = hintsOf(projectDir, "app")
        assertTrue(hintsBefore.hasAspect("LoggingAspect"), hintsBefore)

        // when: no marker left -> incremental round, same class/function names still resolvable
        writeFile(projectDir, "app/src/main/kotlin/Aspect.kt", aspectFileWithoutMarkers())
        writeFile(
            projectDir,
            "app/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 0),
        )
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        val hints = hintsOf(projectDir, "app")
        assertFalse(hints.hasAspect("LoggingAspect"), hints)
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:test")?.outcome, result.output)
    }

    @Test
    fun `removing an aspect's advice during a full rebuild removes its hint`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "app/src/main/kotlin/AspectB.kt", aspectFile("B"))
        runGradle(projectDir, testKitDir(), "compileKotlin")
        val hintsBefore = hintsOf(projectDir, "app")
        assertTrue(hintsBefore.hasAspect("LoggingAspectB"), hintsBefore)

        // when: AspectB.kt still has @Aspect -> full rebuild
        writeFile(projectDir, "app/src/main/kotlin/AspectB.kt", aspectFileWithoutAdvice("B"))
        runGradle(projectDir, testKitDir(), "compileKotlin")

        // then
        val hints = hintsOf(projectDir, "app")
        assertTrue(hints.hasAspect("LoggingAspectA"), hints)
        assertFalse(hints.hasAspect("LoggingAspectB"), hints)
    }

    // aspect                                    LoggingAspect: no advice -> @Before(LogCall)
    // feature*    <- aspect (implementation)    Target [@LogCall] (unchanged)
    @Test
    fun `advice added upstream weaves into an unchanged downstream target`() {
        // given
        writeProject(
            projectDir,
            "aspect" to "",
            "feature" to """implementation(project(":aspect"))""",
        )
        writeFile(projectDir, "aspect/src/main/kotlin/Aspect.kt", aspectFileWithoutAdvice())
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", targetFile())
        runGradle(projectDir, testKitDir(), "compileKotlin")
        val hintsBefore = hintsOf(projectDir, "aspect")
        assertFalse(hintsBefore.hasAspect("LoggingAspect"), hintsBefore)

        // when: only the upstream aspect changes
        writeFile(projectDir, "aspect/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 1),
        )
        val result = runGradle(projectDir, testKitDir(), "test")

        // then: the upstream hint changed, so a weaving failure below is the downstream's fault
        val hintsAfter = hintsOf(projectDir, "aspect")
        assertTrue(hintsAfter.hasAspect("LoggingAspect"), hintsAfter)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature:test")?.outcome, result.output)
    }

    // aspect                                    LoggingAspect: @Before(LogCall) -> no advice
    // feature*    <- aspect (implementation)    Target [@LogCall] (unchanged)
    @Test
    fun `advice removed upstream stops weaving into an unchanged downstream target`() {
        // given
        writeProject(
            projectDir,
            "aspect" to "",
            "feature" to """implementation(project(":aspect"))""",
        )
        writeFile(projectDir, "aspect/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", targetFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 1),
        )
        runGradle(projectDir, testKitDir(), "test")
        val hintsBefore = hintsOf(projectDir, "aspect")
        assertTrue(hintsBefore.hasAspect("LoggingAspect"), hintsBefore)

        // when
        writeFile(projectDir, "aspect/src/main/kotlin/Aspect.kt", aspectFileWithoutAdvice())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 0),
        )
        val result = runGradle(projectDir, testKitDir(), "test")

        // then: the upstream hint is gone, so a weaving failure below is the downstream's fault
        val hintsAfter = hintsOf(projectDir, "aspect")
        assertFalse(hintsAfter.hasAspect("LoggingAspect"), hintsAfter)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature:test")?.outcome, result.output)
    }

    // aspect                                    LoggingAspect: log -> trace
    // feature*    <- aspect (implementation)    Target [@LogCall] (unchanged)
    @Test
    fun `advice renamed upstream is called by its new name downstream`() {
        // given
        writeProject(
            projectDir,
            "aspect" to "",
            "feature" to """implementation(project(":aspect"))""",
        )
        writeFile(projectDir, "aspect/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", targetFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 1),
        )
        runGradle(projectDir, testKitDir(), "test")
        val hintsBefore = hintsOf(projectDir, "aspect")
        assertTrue(hintsBefore.hasFunction("log"), hintsBefore)

        // when
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/Aspect.kt",
            aspectFile().replace("fun log(", "fun trace("),
        )
        val result = runGradle(projectDir, testKitDir(), "test")

        // then: the upstream hint names the new function, so a weaving failure below is the downstream's fault
        val hintsAfter = hintsOf(projectDir, "aspect")
        assertTrue(hintsAfter.hasFunction("trace"), hintsAfter)
        assertFalse(hintsAfter.hasFunction("log"), hintsAfter)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature:test")?.outcome, result.output)
    }

    @Test
    fun `main source set aspect weaves into a test source set target`() {
        // given
        writeProject(projectDir, "app" to "")
        writeFile(projectDir, "app/src/main/kotlin/Aspect.kt", aspectFile())
        // the target lives only in the test source set
        writeFile(projectDir, "app/src/test/kotlin/Target.kt", targetFile())
        writeFile(
            projectDir,
            "app/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 1),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:test")?.outcome, result.output)
    }

    // a                   AspectA
    // b     <- a (api)    AspectB
    // c*    <- b (api)    TargetA, TargetB [@LogCallA/B]
    @Test
    fun `c receives the hints of both a and b through an api chain`() {
        // given
        writeChain(bToA = "api")
        writeFile(projectDir, "c/src/main/kotlin/TargetA.kt", targetFile("A"))
        writeFile(projectDir, "c/src/test/kotlin/WeavingTestA.kt", weavingTestFile(postFix = "A"))

        // when
        val result = runGradle(projectDir, testKitDir(), "test", "dumpHintsPath")

        // then
        assertEquals(listOf("a", "b"), hintsReceivedBy("c").keys.sorted())
        assertEquals(listOf("a"), hintsReceivedBy("b").keys.sorted())
        assertEquals(emptyList<String>(), hintsReceivedBy("a").keys.sorted())
        val receivedByC = hintsReceivedBy("c")
        assertTrue(receivedByC.getValue("a").hasAspect("LoggingAspectA"), receivedByC.toString())
        assertTrue(receivedByC.getValue("b").hasAspect("LoggingAspectB"), receivedByC.toString())
        val receivedByB = hintsReceivedBy("b")
        assertTrue(receivedByB.getValue("a").hasAspect("LoggingAspectA"), receivedByB.toString())
        assertEquals(TaskOutcome.SUCCESS, result.task(":c:test")?.outcome, result.output)
    }

    // a                              AspectA
    // b     <- a (implementation)    AspectB
    // c*    <- b (api)               TargetB [@LogCallB]
    @Test
    fun `c still receives a's hints through an implementation edge`() {
        // given
        writeChain(bToA = "implementation")

        // when
        val result = runGradle(projectDir, testKitDir(), "test", "dumpHintsPath")

        // then
        assertEquals(listOf("a", "b"), hintsReceivedBy("c").keys.sorted())
        val receivedByC = hintsReceivedBy("c")
        assertTrue(receivedByC.getValue("a").hasAspect("LoggingAspectA"), receivedByC.toString())
        assertEquals(TaskOutcome.SUCCESS, result.task(":c:test")?.outcome, result.output)
    }

    // a                                   AspectA
    // b1    <- a (api)
    // b2    <- a (api)
    // c*    <- b1, b2 (implementation)    TargetA [@LogCallA]
    @Test
    fun `a diamond delivers the top module's hints exactly once`() {
        // given
        writeProject(
            projectDir,
            "a" to "",
            "b1" to """api(project(":a"))""",
            "b2" to """api(project(":a"))""",
            "c" to "implementation(project(\":b1\"))\nimplementation(project(\":b2\"))",
        )
        writeFile(projectDir, "a/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "b1/src/main/kotlin/B1.kt", "class B1\n")
        writeFile(projectDir, "b2/src/main/kotlin/B2.kt", "class B2\n")
        writeFile(projectDir, "c/src/main/kotlin/TargetA.kt", targetFile("A"))
        writeFile(projectDir, "c/src/test/kotlin/WeavingTestA.kt", weavingTestFile(postFix = "A"))

        // when
        val result = runGradle(projectDir, testKitDir(), "test", "dumpHintsPath")

        // then
        assertEquals(listOf("a", "b1", "b2"), hintsReceivedBy("c").keys.sorted())
        val receivedByC = hintsReceivedBy("c")
        assertTrue(receivedByC.getValue("a").hasAspect("LoggingAspectA"), receivedByC.toString())
        assertEquals(TaskOutcome.SUCCESS, result.task(":c:test")?.outcome, result.output)
    }

    // a                   AspectA
    // b     <- a (api)    AspectB
    // c*    <- b (api)    TargetA, TargetB [@LogCallA/B]
    @Test
    fun `compiling only the bottom module produces upstream hints first`() {
        // given
        writeChain(bToA = "api")
        writeFile(projectDir, "c/src/main/kotlin/TargetA.kt", targetFile("A"))
        writeFile(projectDir, "c/src/test/kotlin/WeavingTestA.kt", weavingTestFile(postFix = "A"))

        // when
        runGradle(projectDir, testKitDir(), ":c:compileKotlin")

        // then
        assertTrue(hintsOf(projectDir, "a").hasAspect("LoggingAspectA"))
        assertTrue(hintsOf(projectDir, "b").hasAspect("LoggingAspectB"))
        val result = runGradle(projectDir, testKitDir(), ":c:test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":c:test")?.outcome, result.output)
    }

    // a                   AspectA: no advice -> @Before(LogCallA)
    // b     <- a (api)    AspectB
    // c*    <- b (api)    TargetA (unchanged), TargetB
    @Test
    fun `advice added at the top weaves into an unchanged target at the bottom`() {
        // given
        writeChain(bToA = "api", aAspect = aspectFileWithoutAdvice("A"))
        writeFile(projectDir, "c/src/main/kotlin/TargetA.kt", targetFile("A"))
        runGradle(projectDir, testKitDir(), "compileKotlin", "dumpHintsPath")
        val pathsBefore = hintsReceivedBy("c").keys.sorted()
        val receivedBefore = hintsReceivedBy("c").getValue("a")
        assertFalse(receivedBefore.hasAspect("LoggingAspectA"), receivedBefore)

        // when: only a's aspect changes
        writeFile(projectDir, "a/src/main/kotlin/AspectA.kt", aspectFile("A"))
        writeFile(projectDir, "c/src/test/kotlin/WeavingTestA.kt", weavingTestFile(postFix = "A"))
        val result = runGradle(projectDir, testKitDir(), "test", "dumpHintsPath")

        // then
        val receivedAfter = hintsReceivedBy("c").getValue("a")
        assertTrue(receivedAfter.hasAspect("LoggingAspectA"), receivedAfter)
        assertEquals(pathsBefore, hintsReceivedBy("c").keys.sorted())
        assertEquals(TaskOutcome.SUCCESS, result.task(":c:test")?.outcome, result.output)
    }

    private fun writeChain(
        bToA: String,
        aAspect: String = aspectFile("A"),
    ) {
        writeProject(
            projectDir,
            "a" to "",
            "b" to "$bToA(project(\":a\"))",
            "c" to """api(project(":b"))""",
        )
        writeFile(projectDir, "a/src/main/kotlin/AspectA.kt", aAspect)
        writeFile(projectDir, "b/src/main/kotlin/AspectB.kt", aspectFile("B"))
        writeFile(projectDir, "c/src/main/kotlin/TargetB.kt", targetFile("B"))
        writeFile(projectDir, "c/src/test/kotlin/WeavingTestB.kt", weavingTestFile(postFix = "B"))
    }

    // hints.json content handed to [module]'s main compilation, keyed by the module it came from
    private fun hintsReceivedBy(module: String): Map<String, String> = File(projectDir, "$module/build/hints-path.txt")
        .readLines()
        .filter { it.isNotBlank() }
        .associate { it.substringBefore("/build/") to it.substringAfter("\t") }

    // DetectAspectChangeTask's verdict for [module]'s main compilation: "false" or "true:<nanoTime>"
    private fun aspectChangeResult(module: String): String = File(projectDir, "$module/build/generated/aspectk/aspect-change")
        .walkTopDown()
        .first { it.name == "main.txt" }
        .readText()

    private fun String.hasAspect(className: String) = contains("\"class\":\"$className\"")

    private fun String.hasFunction(functionName: String) = contains("\"function\":\"$functionName\"")

    private fun String.records() = Regex("""\{[^{}]*}""").findAll(this).map { it.value }.sorted().toList()
}
