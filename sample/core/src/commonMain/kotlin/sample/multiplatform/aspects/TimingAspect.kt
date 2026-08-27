package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Around
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import sample.multiplatform.annotations.Timed
import sample.multiplatform.platform.currentTimeMillis

/**
 * Measures the execution time of functions annotated with [Timed].
 *
 * Uses `@Around` advice, so it directly controls the original function's execution.
 * It measures the time elapsed before and after calling [ProceedingJoinPoint.proceed],
 * logs it, and passes the original function's return value through to the caller unchanged.
 *
 * Output format: `[TIMED] methodName completed in Xms`
 *
 * ### Example
 * ```kotlin
 * @Timed
 * fun requestPayment(orderId: String, amount: Double): String { ... }
 * // → [TIMED] requestPayment completed in 12ms
 * ```
 */
@Aspect
object TimingAspect {
    /**
     * Provider that returns the current time in milliseconds.
     * Can be swapped out in tests to control the clock.
     */
    var clock: () -> Long = { currentTimeMillis() }

    /** Collected timing results. Used by tests. */
    val timings = mutableListOf<String>()

    /** Timing-log output handler. Can be overridden in tests. */
    var logger: (String) -> Unit = { println(it) }

    @Around(Timed::class)
    fun measure(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.methodName
        val start = clock()
        val result = joinPoint.proceed()
        val elapsed = clock() - start
        val message = "[TIMED] $methodName completed in ${elapsed}ms"
        timings.add(message)
        logger(message)
        return result
    }

    /** Clears the collected timing results. Call this before each test case starts. */
    fun clearTimings() = timings.clear()
}