package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.findAnnotation
import io.github.molelabs.aspectk.runtime.getArgOrNull
import sample.multiplatform.annotations.PreventDoubleClick
import sample.multiplatform.exceptions.DoubleClickException
import sample.multiplatform.platform.currentTimeMillis

/**
 * Prevents duplicate (rapid, repeated) calls to functions annotated with [PreventDoubleClick].
 *
 * Records the last call time per function, and throws [DoubleClickException] to block
 * execution if the function is called again within the cooldown period.
 *
 * ### Example
 * ```kotlin
 * @PreventDoubleClick(cooldownMs = 500L)
 * fun onSubmitButtonClick() { ... }
 * // re-calling within 500ms → DoubleClickException
 * ```
 */
@Aspect
object DebounceAspect {
    /**
     * Provider that returns the current time in milliseconds.
     * Can be swapped out in tests to control the clock.
     */
    var timeProvider: () -> Long = { currentTimeMillis() }

    /** Maps function name → last call time (ms). */
    private val lastCallTime = mutableMapOf<String, Long>()

    @Before(PreventDoubleClick::class)
    fun checkDebounce(joinPoint: JoinPoint) {
        val methodName = joinPoint.signature.methodName
        val now = timeProvider()

        val cooldownMs =
            joinPoint.findAnnotation<PreventDoubleClick>()?.getArgOrNull<Long>("cooldownMs") ?: 1000L

        val lastTime = lastCallTime[methodName] ?: -cooldownMs
        val elapsed = now - lastTime

        if (elapsed < cooldownMs) {
            val remaining = cooldownMs - elapsed
            throw DoubleClickException(
                "Function '$methodName' was called too rapidly. " +
                    "Wait ${remaining}ms before calling again (cooldown: ${cooldownMs}ms).",
            )
        }

        lastCallTime[methodName] = now
    }

    /** Resets the last call time for every function. Use this to isolate test cases. */
    fun reset() = lastCallTime.clear()
}
