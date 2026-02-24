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
package com.mole.aspectk.core

import com.mole.aspectk.core.ir.AspectKIrCompilerContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

// Lightweight tracing interface for logging the duration of IR transformation phases.
// Traces are emitted to the Kotlin compiler's message collector at LOGGING severity and
// appear in build output when verbose compiler logging is enabled.
internal interface Tracer {
    val tag: String
    val description: String

    fun start()

    fun stop()

    // Creates a child tracer one indentation level deeper than this one,
    // for reporting sub-steps within the same logical trace block.
    fun nested(
        description: String,
        tag: String = this.tag,
    ): Tracer
}

// Concrete Tracer implementation that records wall-clock time with a monotonic clock
// and logs indented ▶ / ◀ markers around the timed operation.
// The level field controls indentation; level 0 includes the [tag] prefix.
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

// Convenience wrapper that starts a nested trace on the current scope's tracer,
// runs block, then stops it. The Kotlin contract allows the compiler to treat
// block as called exactly once (enabling smart-casts, definite assignment, etc.).
@OptIn(ExperimentalContracts::class)
internal inline fun <T> TraceScope.traceNested(
    description: String,
    tag: String = tracer.tag,
    block: TraceScope.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return tracer.nested(description, tag).trace(block)
}

// Runs block surrounded by start()/stop() calls, always stopping the tracer even
// if block throws. Returns the result of block.
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

// Factory that creates a SimpleTracer backed by the compiler's message collector.
// The tag is typically the module name; nested tracers inherit it by default.
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

// Scope object threaded through trace { } blocks to allow nested tracing without
// explicitly passing a Tracer reference. Obtained via Tracer.trace { }.
internal interface TraceScope {
    val tracer: Tracer

    companion object {
        operator fun invoke(tracer: Tracer): TraceScope = TraceScopeImpl(tracer)
    }
}

// Inline value class wrapper so TraceScope carries zero runtime overhead.
@JvmInline
internal value class TraceScopeImpl(
    override val tracer: Tracer,
) : TraceScope
