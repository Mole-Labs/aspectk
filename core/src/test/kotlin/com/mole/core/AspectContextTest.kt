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

import com.mole.core.ir.AspectContext
import com.mole.core.ir.AspectContext.Kind
import com.mole.core.ir.AspectLookUp
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class AspectLookUpTest {
    private lateinit var aspectLookUp: AspectLookUp

    private val inheritableClass1: IrClass = mockk(relaxed = true)
    private val inheritableClass2: IrClass = mockk(relaxed = true)
    private val inheritableClass3: IrClass = mockk(relaxed = true)

    private val aspectContext1 = AspectContext(mockIrFunction(), mockIrClassSymbol(), Kind.BEFORE)
    private val aspectContext2 = AspectContext(mockIrFunction(), mockIrClassSymbol(), Kind.BEFORE)
    private val aspectContext3 = AspectContext(mockIrFunction(), mockIrClassSymbol(), Kind.BEFORE)

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
    fun `add and get inheritable IrClasses for a single FqName`() {
        // given
        aspectLookUp = AspectLookUp()
        val fqName = FqName("com.example.InheritableTarget1")

        // when
        aspectLookUp.addInheritable(fqName, inheritableClass1)
        aspectLookUp.addInheritable(fqName, inheritableClass2)

        // then
        val inheritable = aspectLookUp.getInheritable(fqName)
        assertAll(
            { assertEquals(2, inheritable.size) },
            { assertTrue(inheritable.contains(inheritableClass1)) },
            { assertTrue(inheritable.contains(inheritableClass2)) },
        )
    }

    @Test
    fun `add and get inheritable IrClasses for multiple FqNames`() {
        // given
        aspectLookUp = AspectLookUp()
        val fqName1 = FqName("com.example.InheritableTarget1")
        val fqName2 = FqName("com.example.InheritableTarget2")

        // when
        aspectLookUp.addInheritable(fqName1, inheritableClass1)
        aspectLookUp.addInheritable(fqName2, inheritableClass2)
        aspectLookUp.addInheritable(fqName1, inheritableClass3)

        // then
        val inheritable1 = aspectLookUp.getInheritable(fqName1)
        val inheritable2 = aspectLookUp.getInheritable(fqName2)

        assertAll(
            { assertEquals(2, inheritable1.size) },
            { assertEquals(1, inheritable2.size) },
            { assertTrue(inheritable1.contains(inheritableClass1)) },
            { assertTrue(inheritable1.contains(inheritableClass3)) },
            { assertTrue(inheritable2.contains(inheritableClass2)) },
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
        aspectLookUp.addInheritable(fqName3, inheritableClass1)

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
    fun `thread-safe addition of AspectContexts`() =
        runBlocking(Dispatchers.Default) {
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
    fun `thread-safe addition of inheritable IrClasses`() =
        runBlocking(Dispatchers.Default) {
            // given
            aspectLookUp = AspectLookUp()
            val numThreads = 10
            val numAddsPerThread = 100
            val fqName = FqName("com.example.ConcurrentInheritableTarget")

            // when
            val jobs =
                List(numThreads) {
                    launch {
                        repeat(numAddsPerThread) {
                            val irClass = mockIrClass()
                            aspectLookUp.addInheritable(fqName, irClass)
                        }
                    }
                }
            jobs.joinAll()

            // then
            val inheritable = aspectLookUp.getInheritable(fqName)
            assertEquals(numThreads * numAddsPerThread, inheritable.size)

            val distinctIrClasses = ConcurrentHashMap.newKeySet<IrClass>()
            inheritable.forEach { distinctIrClasses.add(it) }
            assertEquals(numThreads * numAddsPerThread, distinctIrClasses.size)
        }
}

fun mockIrFunction(): IrFunction = mockk(relaxed = true)

fun mockIrClass(): IrClass = mockk(relaxed = true)

fun mockIrClassSymbol(): IrClassSymbol = mockk(relaxed = true)
