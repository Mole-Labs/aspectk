package sample.multiplatform.annotations

/**
 * Prevents the same function from being called again within a short time window.
 * Throws [sample.multiplatform.exceptions.DoubleClickException] if the function is
 * re-invoked before the cooldown period elapses.
 *
 * @param cooldownMs Minimum interval, in milliseconds, required between calls. Defaults to 1000ms (1 second).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PreventDoubleClick(
    val cooldownMs: Long = 1000L,
)
