package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.findAnnotation
import io.github.molelabs.aspectk.runtime.getArgOrNull
import sample.multiplatform.annotations.LogExecution

/**
 * Intercepts calls to functions annotated with [LogExecution] and logs them to the console.
 *
 * Output format: `[LEVEL][TAG] → methodName(param1=value1, param2=value2) : ReturnType`
 *
 * ### Example
 * ```kotlin
 * @LogExecution(tag = "UserService", level = "INFO")
 * fun login(username: String, password: String): Boolean { ... }
 * // → [INFO][UserService] → login(username=alice, password=***) : Boolean
 * ```
 */
@Aspect
object LoggingAspect {
    /** Log-output handler. Can be overridden in tests to capture the output. */
    var logger: (String) -> Unit = { message -> println(message) }

    /** Collected log messages. Used by tests. */
    val logs = mutableListOf<String>()

    @Before(LogExecution::class)
    fun log(joinPoint: JoinPoint) {
        val signature = joinPoint.signature

        // Extract the tag and level parameters from the @LogExecution annotation
        val annotationInfo = joinPoint.findAnnotation<LogExecution>()
        val tag = annotationInfo?.getArgOrNull<String>("tag") ?: "ASPECTK"
        val level = annotationInfo?.getArgOrNull<String>("level") ?: "DEBUG"

        // Build the parameter list (args[0] is the receiver, args[1..] are the parameters)
        val paramStr =
            signature.parameter
                .drop(1)
                .mapIndexed { index, param ->
                    val value = joinPoint.args.getOrNull(index)
                    val displayValue = if (param.name.contains("password", ignoreCase = true)) "***" else value
                    "${param.name}=$displayValue"
                }.joinToString(", ")

        val message = "[$level][$tag] → ${signature.methodName}($paramStr) : ${signature.returnTypeName.substringAfterLast('.')}"
        logs.add(message)
        logger(message)
    }

    fun clearLogs() = logs.clear()
}
