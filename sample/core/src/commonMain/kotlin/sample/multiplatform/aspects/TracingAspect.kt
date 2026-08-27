package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.findAnnotation
import io.github.molelabs.aspectk.runtime.getArgOrNull
import sample.multiplatform.annotations.Trace

/**
 * Traces calls to functions annotated with [Trace] hierarchically.
 *
 * Indents output based on call depth to visualize the function-call tree.
 *
 * ### Example output
 * ```
 * [TRACE] → processOrder (depth=1)
 * [TRACE]   → validatePayment (depth=2)
 * [TRACE]     → chargeCard (depth=3)
 * ```
 *
 * ### Example usage
 * ```kotlin
 * @Trace(spanName = "validate-payment")
 * fun validatePayment(orderId: String) { ... }
 * ```
 */
@Aspect
object TracingAspect {
    /** Stack of spans currently being traced. Used to compute the indentation depth. */
    val callStack = mutableListOf<String>()

    /** Trace-output handler. Can be overridden in tests. */
    var logger: (String) -> Unit = { message -> println(message) }

    @Before(Trace::class)
    fun trace(joinPoint: JoinPoint) {
        val spanName =
            joinPoint
                .findAnnotation<Trace>()
                ?.getArgOrNull<String>("spanName")
                ?.takeIf { it.isNotEmpty() } ?: joinPoint.signature.methodName

        val depth = callStack.size
        val indent = "  ".repeat(depth)
        callStack.add(spanName)

        val message = "[TRACE] $indent→ $spanName (depth=${depth + 1})"
        logger(message)
    }

    /** Clears the stack. Call this before each test case starts. */
    fun clearStack() = callStack.clear()
}
