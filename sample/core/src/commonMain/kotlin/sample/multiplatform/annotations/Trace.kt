package sample.multiplatform.annotations

/**
 * Traces the function-call hierarchy (stack depth) and emits tracing logs.
 *
 * @param spanName Name of the tracing span. Falls back to the function name if empty.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Trace(
    val spanName: String = "",
)
