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

import io.github.molelabs.aspectk.core.ir.AspectContext
import io.github.molelabs.aspectk.core.ir.AspectContext.Kind
import io.github.molelabs.aspectk.core.ir.AspectLookUp
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class AspectLookUpTest {
    private lateinit var aspectLookUp: AspectLookUp

    private val aspectContext1 = AspectContext(mockIrFunction(), mockIrClassSymbol(), Kind.BEFORE, false)
    private val aspectContext2 = AspectContext(mockIrFunction(), mockIrClassSymbol(), Kind.BEFORE, false)
    private val aspectContext3 = AspectContext(mockIrFunction(), mockIrClassSymbol(), Kind.BEFORE, false)

    @Test
    fun `add and get AspectContexts for a single FqName`() {
        // given
        aspectLookUp = AspectLookUp()
        val fqName = FqName("com.example.Target1")

        // when
        aspectLookUp.add(fqName, aspectContext1)
        aspectLookUp.add(fqName, aspectContext2)

        // then
        val contexts = aspectLookUp[fqName]
        assertAll(
            { assertEquals(2, contexts.size) },
            { assertTrue(contexts.contains(aspectContext1)) },
            { assertTrue(contexts.contains(aspectContext2)) },
        )
    }

    @Test
    fun `add and get AspectContexts for multiple FqNames`() {
        // given
        aspectLookUp = AspectLookUp()
        val fqName1 = FqName("com.example.Target1")
        val fqName2 = FqName("com.example.Target2")

        // when
        aspectLookUp.add(fqName1, aspectContext1)
        aspectLookUp.add(fqName2, aspectContext2)
        aspectLookUp.add(fqName1, aspectContext3)

        // then
        val contexts1 = aspectLookUp[fqName1]
        val contexts2 = aspectLookUp[fqName2]

        assertAll(
            { assertEquals(2, contexts1.size) },
            { assertTrue(contexts1.contains(aspectContext1)) },
            { assertTrue(contexts1.contains(aspectContext3)) },
            { assertEquals(1, contexts2.size) },
            { assertTrue(contexts2.contains(aspectContext2)) },
        )
    }

    @Test
    fun `targets property should reflect added FqNames`() {
        // given
        aspectLookUp = AspectLookUp()
        val fqName1 = FqName("com.example.Target1")
        val fqName2 = FqName("com.example.Target2")
        val fqName3 = FqName("com.example.Target3")

        // when
        aspectLookUp.add(fqName1, aspectContext1)
        aspectLookUp.add(fqName2, aspectContext2)

        // then
        val targets = aspectLookUp.targets
        assertAll(
            { assertEquals(2, targets.size) },
            { assertTrue(targets.contains(fqName1)) },
            { assertTrue(targets.contains(fqName2)) },
            { assertFalse(targets.contains(fqName3)) },
        )
    }

    @Test
    fun `add and get overridden declarations for a single IrElement`() {
        // given
        aspectLookUp = AspectLookUp()
        val irElement1 = mockIrElement()
        val target1 = mockFqName()

        // when
        aspectLookUp.addOverridden(irElement1, target1)

        // then
        assertTrue(aspectLookUp.getOverridden(irElement1).contains(target1))
    }

    @Test
    fun `getOverridden should return false for a non-overridden IrElement`() {
        // given
        aspectLookUp = AspectLookUp()
        val irElement1 = mockIrElement()
        val irElement2 = mockIrElement()
        val target1 = mockFqName()

        // when
        aspectLookUp.addOverridden(irElement1, target1)

        // then
        assertFalse(aspectLookUp.getOverridden(irElement2).contains(target1))
    }

    @Test
    fun `add and get overridden declarations for multiple IrElements`() {
        // given
        aspectLookUp = AspectLookUp()
        val irElement1 = mockIrElement()
        val irElement2 = mockIrElement()
        val irElement3 = mockIrElement()
        val target1 = mockFqName()
        val target2 = mockFqName()

        // when
        aspectLookUp.addOverridden(irElement1, target1)
        aspectLookUp.addOverridden(irElement1, target2)
        aspectLookUp.addOverridden(irElement2, target2)

        // then
        assertAll(
            { assertTrue(aspectLookUp.getOverridden(irElement1).contains(target1)) },
            { assertTrue(aspectLookUp.getOverridden(irElement1).contains(target2)) },
            { assertTrue(aspectLookUp.getOverridden(irElement2).contains(target2)) },
            { assertFalse(aspectLookUp.getOverridden(irElement3).contains(target1)) },
        )
    }

    @Test
    fun `thread-safe addition of AspectContexts`() = runBlocking(Dispatchers.Default) {
        // given
        aspectLookUp = AspectLookUp()
        val numThreads = 10
        val numAddsPerThread = 100
        val fqName = FqName("com.example.ConcurrentTarget")

        // when
        val jobs =
            List(numThreads) {
                launch {
                    repeat(numAddsPerThread) {
                        val context =
                            AspectContext(
                                mockIrFunction(),
                                mockIrClassSymbol(),
                                Kind.BEFORE,
                                false,
                            )
                        aspectLookUp.add(fqName, context)
                    }
                }
            }
        jobs.joinAll()

        // then
        val contexts = aspectLookUp[fqName]
        val distinctContexts = ConcurrentHashMap.newKeySet<AspectContext>()

        assertEquals(numThreads * numAddsPerThread, contexts.size)
        contexts.forEach { distinctContexts.add(it) }
        assertEquals(numThreads * numAddsPerThread, distinctContexts.size)
    }

    @Test
    fun `thread-safe addition of overridden declarations for multiple IrElements`() = runBlocking(Dispatchers.Default) {
        // given
        aspectLookUp = AspectLookUp()
        val numThreads = 10
        val numAddsPerThread = 100
        val irElement = mockIrElement()

        // when
        val jobs =
            List(numThreads) { threadId ->
                launch {
                    repeat(numAddsPerThread) { i ->
                        val target = FqName("com.example.ConcurrentTarget${i}_$threadId")
                        aspectLookUp.addOverridden(irElement, target)
                    }
                }
            }
        jobs.joinAll()

        // then
        val contexts = aspectLookUp.getOverridden(irElement)
        val distinctContexts = ConcurrentHashMap.newKeySet<FqName>()

        assertEquals(numThreads * numAddsPerThread, contexts.size)
        contexts.forEach { distinctContexts.add(it) }
        assertEquals(numThreads * numAddsPerThread, distinctContexts.size)
    }
}

fun mockIrFunction(): IrFunction = mockk(relaxed = true)

fun mockIrClassSymbol(): IrClassSymbol = mockk(relaxed = true)

fun mockIrElement(): IrElement = mockk(relaxed = true)

fun mockFqName(): FqName = mockk(relaxed = true)
