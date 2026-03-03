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
import com.tschuchort.compiletesting.SourceFile
import io.github.molelabs.aspectk.runtime.MethodParameter
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
