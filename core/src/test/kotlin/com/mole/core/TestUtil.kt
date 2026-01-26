package com.mole.core

import com.mole.runtime.MethodParameter
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.junit.jupiter.api.Assertions.assertNotNull
import java.net.URLClassLoader

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    sourceFiles: List<SourceFile>,
    plugin: CompilerPluginRegistrar = AspectKCompilerPluginRegistrar(),
): JvmCompilationResult =
    KotlinCompilation()
        .apply {
            noOptimize = true
            kotlincArguments +=
                listOf(
                    "-jvm-default=no-compatibility",
                    "-Xverify-ir=error",
                    "-Xverify-ir-visibility",
                    "-Xno-inline",
                    "-Xno-optimize",
                )
            jvmDefault = JvmDefaultMode.DISABLE.description
            languageVersion = "2.2"
            sources = sourceFiles
            this.compilerPluginRegistrars = listOf(plugin)
            inheritClassPath = true
        }.compile()

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    source: String,
    name: String = "aspectk-test.kt",
    plugin: CompilerPluginRegistrar = AspectKCompilerPluginRegistrar(),
): JvmCompilationResult =
    compile(
        listOf(
            SourceFile.kotlin(name = name, contents = source),
        ),
        plugin,
    )

fun URLClassLoader.assertAndGetField(
    className: String,
    fieldName: String,
    targetClass: String? = null,
): Any =
    this
        .loadClass(className)
        .getDeclaredField(fieldName)
        .apply {
            setAccessible(true)
            assertNotNull(this@apply)
        }.get(targetClass)

fun URLClassLoader.thisParameterInfo(className: String = "Test"): MethodParameter =
    MethodParameter(
        name = "<this>",
        type = loadClass(className).kotlin,
        typeName = className,
        annotations = listOf(),
        isNullable = false,
    )
