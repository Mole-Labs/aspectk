package sample.multiplatform.annotations

/**
 * Measures a function's execution time in milliseconds and logs the result.
 *
 * [sample.multiplatform.aspects.TimingAspect] uses an `@Around` advice to measure the
 * time elapsed before and after the
 * [ProceedingJoinPoint.proceed][io.github.molelabs.aspectk.runtime.ProceedingJoinPoint.proceed]
 * call.
 *
 * ### Example
 * ```kotlin
 * @Timed
 * fun requestPayment(orderId: String, amount: Double): String { ... }
 * // → [TIMED] requestPayment completed in 12ms
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Timed