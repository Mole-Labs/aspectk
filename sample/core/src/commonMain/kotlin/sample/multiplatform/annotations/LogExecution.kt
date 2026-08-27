package sample.multiplatform.annotations

/**
 * Automatically logs the method name, parameters, and return type whenever the
 * annotated function is called.
 *
 * @param tag Tag used in the log output. Defaults to "ASPECTK".
 * @param level Log level string (e.g. "DEBUG", "INFO", "WARN"). Defaults to "DEBUG".
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class LogExecution(
    val tag: String = "ASPECTK",
    val level: String = "DEBUG",
)
