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
package com.mole.core

import com.mole.core.ir.AspectKIrCompilerContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal interface Tracer {
    val tag: String
    val description: String

    fun start()

    fun stop()

    fun nested(
        description: String,
        tag: String = this.tag,
    ): Tracer
}

private class SimpleTracer(
    override val tag: String,
    override val description: String,
    private val level: Int,
    private val log: (String) -> Unit,
) : Tracer {
    private var mark: ValueTimeMark? = null
    private inline val running
        get() = mark != null

    override fun start() {
        check(!running) { "Tracer already started" }
        val tagPrefix = if (level == 0) "[$tag] " else ""
        log("$tagPrefix${"  ".repeat(level)}▶ $description")
        mark = TimeSource.Monotonic.markNow()
    }

    override fun stop() {
        check(running) { "Tracer not started" }
        val elapsed = mark!!.elapsedNow()
        mark = null
        val tagPrefix = if (level == 0) "[$tag] " else ""
        log("$tagPrefix${"  ".repeat(level)}◀ $description (${elapsed.inWholeMilliseconds} ms)")
    }

    override fun nested(
        description: String,
        tag: String,
    ): Tracer = SimpleTracer(tag, description, level + 1, log)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> TraceScope.traceNested(
    description: String,
    tag: String = tracer.tag,
    block: TraceScope.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return tracer.nested(description, tag).trace(block)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> Tracer.trace(block: TraceScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    start()
    try {
        return TraceScope(this).block()
    } finally {
        stop()
    }
}

@Suppress("DEPRECATION")
internal fun AspectKIrCompilerContext.tracer(
    tag: String,
    description: String,
    level: Int = 0,
): Tracer = SimpleTracer(
    tag,
    description,
    level,
) {
    pluginContext.messageCollector
        .report(CompilerMessageSeverity.LOGGING, it)
}

internal interface TraceScope {
    val tracer: Tracer

    companion object {
        operator fun invoke(tracer: Tracer): TraceScope = TraceScopeImpl(tracer)
    }
}

@JvmInline
internal value class TraceScopeImpl(
    override val tracer: Tracer,
) : TraceScope
