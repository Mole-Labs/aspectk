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

import io.github.molelabs.aspectk.runtime.MethodParameter
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.junit.jupiter.api.Assertions.assertNotNull
import java.net.URLClassLoader

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    sourceFiles: List<SourceFile>,
    plugin: CompilerPluginRegistrar = AspectKCompilerPluginRegistrar(),
): JvmCompilationResult = KotlinCompilation()
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

fun ClassLoader.execute(
    vararg args: Any?,
    className: String = "Test",
    methodName: String = "test1",
): Any? {
    val testClass = loadClass(className)
    val method =
        testClass.declaredMethods.find { it.name == methodName } ?: error("Method not found")
    val instance = testClass.getDeclaredConstructor().newInstance()
    return method.invoke(instance, *args)
}

fun ClassLoader.getAspectField(
    className: String = "ExampleAspect",
    fieldName: String = "executed",
): Any {
    val aspectObject =
        loadClass(className)
            .getField("INSTANCE")
            .get(null)
    val executedField = aspectObject::class.java.getDeclaredField(fieldName)
    executedField.isAccessible = true
    return executedField.get(aspectObject)
}
