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
package io.github.molelabs.aspectk.core

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.github.molelabs.aspectk.runtime.MethodParameter
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.io.File
import java.net.URLClassLoader
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    sourceFiles: List<SourceFile>,
    plugin: CompilerPluginRegistrar = AspectKCompilerPluginRegistrar(),
): JvmCompilationResult = KotlinCompilation()
    .apply {
        jvmDefault = JvmDefaultMode.DISABLE.description
        jvmTarget = "17"
        languageVersion = "2.3"
        sources = sourceFiles
        verbose = true
        this.compilerPluginRegistrars = listOf(plugin)
        inheritClassPath = true
    }.compile()

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    @Language("kotlin") vararg source: String,
    name: String = "aspectk-test.kt",
    plugin: CompilerPluginRegistrar = AspectKCompilerPluginRegistrar(),
): JvmCompilationResult = compile(
    source.mapIndexed { idx, source ->
        SourceFile.kotlin(name = "${idx}_$name", contents = source)
    },
    plugin,
)

@OptIn(ExperimentalCompilerApi::class)
fun compileWithHints(
    sourceFiles: List<SourceFile>,
    hintsOutputDir: File? = null,
    hintsPaths: List<File> = emptyList(),
    extraClasspath: List<File> = emptyList(),
    moduleName: String? = null,
): JvmCompilationResult = KotlinCompilation()
    .apply {
        moduleName?.let { this.moduleName = it }
        jvmDefault = JvmDefaultMode.DISABLE.description
        jvmTarget = "17"
        languageVersion = "2.3"
        sources = sourceFiles
        verbose = true
        compilerPluginRegistrars = listOf(AspectKCompilerPluginRegistrar())
        commandLineProcessors = listOf(AspectKCommandLineProcessor())
        pluginOptions =
            buildList {
                hintsOutputDir?.let {
                    add(PluginOption("io.github.mole-labs.aspectk", AspectKCommandLineProcessor.HINTS_OUTPUT_DIR_OPTION, it.absolutePath))
                }
                hintsPaths.forEach {
                    add(PluginOption("io.github.mole-labs.aspectk", AspectKCommandLineProcessor.HINTS_PATH_OPTION, it.absolutePath))
                }
            }
        inheritClassPath = true
        classpaths = classpaths + extraClasspath
    }.compile()

/**
 * Compiles [modules] in order, each one against the classes and hints of every module before it,
 * as if each depended on all earlier ones through `api`, and loads all of them in one class loader.
 */
@OptIn(ExperimentalCompilerApi::class)
fun compileModules(vararg modules: Pair<String, List<SourceFile>>): ClassLoader {
    val outputs = mutableListOf<File>()
    val hintsDirs = mutableListOf<File>()
    modules.forEach { (name, sources) ->
        val hintsDir = createTempDirectory("aspectk-hints-$name").toFile()
        val result =
            compileWithHints(
                sourceFiles = sources,
                hintsOutputDir = hintsDir,
                hintsPaths = hintsDirs.toList(),
                extraClasspath = outputs.toList(),
                moduleName = name,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "module $name: ${result.messages}")
        outputs += result.outputDirectory
        hintsDirs += hintsDir
    }
    return URLClassLoader(outputs.map { it.toURI().toURL() }.toTypedArray(), AdviceKind::class.java.classLoader)
}

/** The advice types a weaving test can run under. */
enum class AdviceKind(
    private val annotation: String,
    private val joinPoint: String,
) {
    BEFORE("Before", "JoinPoint"),
    AFTER("After", "JoinPoint"),
    AROUND("Around", "ProceedingJoinPoint"),
    ;

    /**
     * An `@Aspect object [name]` whose single advice of this kind on [targets] runs [record] and,
     * for @Around, then proceeds with the original arguments. The advice's parameter is `joinPoint`.
     * For a [suspendTarget], @Around takes a SuspendProceedingJoinPoint and suspends itself.
     */
    fun aspect(
        name: String,
        targets: String,
        record: String,
        inherits: Boolean = false,
        suspendTarget: Boolean = false,
    ): String {
        val suspendAround = this == AROUND && suspendTarget
        val joinPoint = if (suspendAround) "SuspendProceedingJoinPoint" else joinPoint
        val modifier = if (suspendAround) "suspend " else ""
        val proceed = if (this == AROUND) "return joinPoint.proceed()" else ""
        val returnType = if (this == AROUND) ": Any?" else ""
        return """
            @io.github.molelabs.aspectk.runtime.Aspect

            object $name {
                @io.github.molelabs.aspectk.runtime.$annotation($targets, inherits = $inherits)
                ${modifier}fun advice(joinPoint: io.github.molelabs.aspectk.runtime.$joinPoint)$returnType {
                    $record
                    $proceed
                }
            }
        """
    }
}

fun URLClassLoader.assertAndGetField(
    className: String,
    fieldName: String,
    targetClass: String? = null,
): Any = this
    .loadClass(className)
    .getDeclaredField(fieldName)
    .apply {
        setAccessible(true)
        assertNotNull(this@apply)
    }.get(targetClass)

fun ClassLoader.thisParameterInfo(className: String = "Test"): MethodParameter = MethodParameter(
    name = "<this>",
    type = loadClass(className).kotlin,
    typeName = className,
    annotations = listOf(),
    isNullable = false,
)
