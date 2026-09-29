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

// End-to-end weaving across modules on clean builds. Every advice appends its tag to
// shared.CallLog (declared in the `shared` module), so tests assert on which advices ran and
// in what order. Covers @Aspect declared across modules and target annotations across modules.
// Graphs above each test list every module as `name  <- dependencies  contents`; `*` marks the
// module running the weaving test and `-> "X"` the tag an advice appends to CallLog.
class CrossModuleWeavingTest {
    @TempDir
    lateinit var projectDir: File

    // shared                               LogCall, CallLog
    // aspect-a    <- shared (api)          AspectA -> "A"
    // aspect-b    <- shared (api)          AspectB -> "B"
    // feature*    <- aspect-a, aspect-b    Target [@LogCall]
    @Test
    fun `aspects from two modules both weave into the same target`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "aspect-a" to """api(project(":shared"))""",
            "aspect-b" to """api(project(":shared"))""",
            "feature" to "implementation(project(\":aspect-a\"))\nimplementation(project(\":aspect-b\"))",
        )
        writeShared()
        writeFile(
            projectDir,
            "aspect-a/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A"),
        )
        writeFile(
            projectDir,
            "aspect-b/src/main/kotlin/AspectB.kt",
            callLogAspectFile("AspectB", tag = "B"),
        )
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", callLogTargetFile())
        // order across external modules follows Gradle resolution, so only membership is pinned
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "Target().run()",
                """assertEquals(listOf("A", "B"), CallLog.calls.sorted())""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":feature:test")
    }

    // shared                         LogCall, CallLog
    // aspect      <- shared (api)    ExternalAspect -> "external"
    // feature*    <- aspect          LocalAspect -> "local", Target [@LogCall]
    @Test
    fun `local advice runs before external advice on the same target`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "aspect" to """api(project(":shared"))""",
            "feature" to """implementation(project(":aspect"))""",
        )
        writeShared()
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/ExternalAspect.kt",
            callLogAspectFile("ExternalAspect", tag = "external"),
        )
        writeFile(
            projectDir,
            "feature/src/main/kotlin/LocalAspect.kt",
            callLogAspectFile("LocalAspect", tag = "local"),
        )
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", callLogTargetFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "Target().run()",
                """assertEquals(listOf("local", "external"), CallLog.calls)""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":feature:test")
    }

    // shared                               LogCall, CallLog
    // aspect-a    <- shared (api)          a.LoggingAspect.log -> "a"
    // aspect-b    <- shared (api)          b.LoggingAspect.log -> "b"
    // feature*    <- aspect-a, aspect-b    Target [@LogCall]
    @Test
    fun `same aspect and advice names in different packages stay separate`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "aspect-a" to """api(project(":shared"))""",
            "aspect-b" to """api(project(":shared"))""",
            "feature" to "implementation(project(\":aspect-a\"))\nimplementation(project(\":aspect-b\"))",
        )
        writeShared()
        writeFile(
            projectDir,
            "aspect-a/src/main/kotlin/LoggingAspect.kt",
            callLogAspectFile("LoggingAspect", tag = "a", pkg = "a"),
        )
        writeFile(
            projectDir,
            "aspect-b/src/main/kotlin/LoggingAspect.kt",
            callLogAspectFile("LoggingAspect", tag = "b", pkg = "b"),
        )
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", callLogTargetFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "Target().run()",
                """assertEquals(listOf("a", "b"), CallLog.calls.sorted())""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":feature:test")
    }

    // shared                         LogCall, CallLog
    // aspect*     <- shared (api)    AspectA -> "A", AspectModuleTarget [@LogCall]
    // feature*    <- aspect          Target [@LogCall]
    @Test
    fun `the aspect module weaves its own targets and its dependents' targets`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "aspect" to """api(project(":shared"))""",
            "feature" to """implementation(project(":aspect"))""",
        )
        writeShared()
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A"),
        )
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectModuleTarget.kt",
            callLogTargetFile("AspectModuleTarget"),
        )
        writeFile(
            projectDir,
            "aspect/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "AspectModuleTarget().run()",
                """assertEquals(listOf("A"), CallLog.calls)""",
            ),
        )
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", callLogTargetFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "Target().run()",
                """assertEquals(listOf("A"), CallLog.calls)""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":aspect:test", ":feature:test")
    }

    // shared                                    LogCall, CallLog
    // aspect      <- shared (implementation)    AspectA -> "A"
    // feature*    <- shared, aspect             Target [@LogCall]
    @Test
    fun `annotation, aspect and target each live in their own module`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "aspect" to """implementation(project(":shared"))""",
            "feature" to "implementation(project(\":shared\"))\nimplementation(project(\":aspect\"))",
        )
        writeShared()
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A"),
        )
        writeFile(projectDir, "feature/src/main/kotlin/Target.kt", callLogTargetFile())
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "Target().run()",
                """assertEquals(listOf("A"), CallLog.calls)""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":feature:test")
    }

    // shared                                     LogCall, CallLog
    // mod1                                       m1.First
    // mod2                                       m2.Second
    // aspect      <- shared, mod1, mod2 (api)    AspectA on [First, Second] -> "A"
    // feature*    <- aspect                      Target.first [@First], Target.second [@Second]
    @Test
    fun `one advice targets annotations declared in two different modules`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "mod1" to "",
            "mod2" to "",
            "aspect" to "api(project(\":shared\"))\napi(project(\":mod1\"))\napi(project(\":mod2\"))",
            "feature" to """implementation(project(":aspect"))""",
        )
        writeShared()
        writeFile(
            projectDir,
            "mod1/src/main/kotlin/First.kt",
            "package m1\n\nannotation class First\n",
        )
        writeFile(
            projectDir,
            "mod2/src/main/kotlin/Second.kt",
            "package m2\n\nannotation class Second\n",
        )
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A", targets = listOf("m1.First", "m2.Second")),
        )
        writeFile(
            projectDir,
            "feature/src/main/kotlin/Target.kt",
            """
            class Target {
                @m1.First
                fun first() {}

                @m2.Second
                fun second() {}
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "Target().first()",
                "Target().second()",
                """assertEquals(listOf("A", "A"), CallLog.calls)""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":feature:test")
    }

    // Top-level targets put `$MethodSignatures$<file>` into the file's package; two modules
    // sharing a package and a file name emit the same class name onto one classpath. JvmName
    // keeps Kotlin's own file facades distinct so only the plugin-generated class can collide; both
    // modules number their signature fields from ajc$tjp_0, so a collision silently hands runB
    // runA's MethodSignature instead of crashing -- hence asserting on methodName.
    //
    // shared                                  LogCall, CallLog
    // aspect       <- shared (api)            AspectA -> "A:<methodName>"
    // feature-a    <- aspect (api)            Targets.kt: runA [@LogCall]
    // feature-b    <- aspect (api)            Targets.kt: runB [@LogCall]
    // app*         <- feature-a, feature-b
    @Test
    fun `two modules with same-named files of top-level targets weave independently`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "aspect" to """api(project(":shared"))""",
            "feature-a" to """api(project(":aspect"))""",
            "feature-b" to """api(project(":aspect"))""",
            "app" to "implementation(project(\":feature-a\"))\nimplementation(project(\":feature-b\"))",
        )
        writeShared()
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A", withMethodName = true),
        )
        writeFile(
            projectDir,
            "feature-a/src/main/kotlin/Targets.kt",
            "@file:JvmName(\"FeatureA\")\n\n@shared.LogCall\nfun runA() {}\n",
        )
        writeFile(
            projectDir,
            "feature-b/src/main/kotlin/Targets.kt",
            "@file:JvmName(\"FeatureB\")\n\n@shared.LogCall\nfun runB(value: Int) {}\n",
        )
        writeFile(projectDir, "app/src/main/kotlin/App.kt", "class App\n")
        writeFile(
            projectDir,
            "app/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile(
                "runA()",
                "runB(1)",
                """assertEquals(listOf("A:runA", "A:runB"), CallLog.calls)""",
            ),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":app:test")
    }

    // shared                         LogCall, CallLog
    // base        <- shared (api)    Base.run [@LogCall]
    // aspect      <- shared (api)    AspectA (inherits) -> "A"
    // feature*    <- base, aspect    Child : Base [override]
    @Test
    fun `inherits advice weaves into an override of a function declared in another module`() {
        // given
        writeProject(
            projectDir,
            "shared" to "",
            "base" to """api(project(":shared"))""",
            "aspect" to """api(project(":shared"))""",
            "feature" to "implementation(project(\":base\"))\nimplementation(project(\":aspect\"))",
        )
        writeShared()
        writeFile(
            projectDir,
            "base/src/main/kotlin/Base.kt",
            """
            open class Base {
                @shared.LogCall
                open fun run() {}
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "aspect/src/main/kotlin/AspectA.kt",
            callLogAspectFile("AspectA", tag = "A", inherits = true),
        )
        writeFile(
            projectDir,
            "feature/src/main/kotlin/Child.kt",
            """
            class Child : Base() {
                override fun run() {}
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "feature/src/test/kotlin/WeavingTest.kt",
            callLogWeavingTestFile("Child().run()", """assertEquals(listOf("A"), CallLog.calls)"""),
        )

        // when
        val result = runGradle(projectDir, testKitDir(), "test")

        // then
        assertTestsPass(result, ":feature:test")
    }

    // endregion

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

    private fun writeShared() {
        writeFile(projectDir, "shared/src/main/kotlin/Shared.kt", sharedModuleFile())
    }
}
