package io.github.molelabs.aspectk.core.ir

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MultipleAdviceCallOrderTest {
    // TODO support multiple @Around, @After annotations
    @Test
    fun `@Around advice is only invoked once per function`() {
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@After advice is only invoked once per function`() {
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@After and @Around advice is only invoked once per function`() {
    }

    @Test
    fun `multiple @Before advice is ordinally invoked`() {
    }

    @Test
    fun `multiple @Before and @Around advice is ordinally invoked`() {
       /*
       Current architectural limitations and the absence of an advice ordering engine
       restrict how multiple advices are applied. Specifically, @Before advice only
       functions correctly when positioned relative to @Around or @After interceptors,
       as the latter types effectively override the original function body
        */
    }

    @Test
    fun `multiple @Before and @After advice is ordinally invoked`() {
        /*
        Current architectural limitations and the absence of an advice ordering engine
        restrict how multiple advices are applied. Specifically, @Before advice only
        functions correctly when positioned relative to @Around or @After interceptors,
        as the latter types effectively override the original function body
         */
    }
}
