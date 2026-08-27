package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.After
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.findAnnotation
import io.github.molelabs.aspectk.runtime.getArgOrNull
import sample.multiplatform.annotations.AuditAction

/**
 * Records calls to functions (and their overriding methods) annotated with [AuditAction]
 * as audit log entries.
 *
 * Uses `@After` advice, so the log is written **after** the target function returns
 * successfully — capturing the moment the action actually completed.
 *
 * With `inherits = true`, advice is automatically applied to overriding methods in
 * subclasses even when only the parent class carries [AuditAction].
 *
 * Output format: `[AUDIT] action=<action> | method=<methodName>`
 *
 * ### Example
 * ```kotlin
 * @Aspect
 * object AuditAspect {
 *     @After(AuditAction::class, inherits = true)
 *     fun audit(joinPoint: JoinPoint) { ... }
 * }
 * ```
 */
@Aspect
object AuditAspect {
    /** Audit-log output handler. Can be overridden in tests to capture the output. */
    var auditLogger: (String) -> Unit = { println(it) }

    /** Collected audit log entries. Used by tests. */
    val auditLogs = mutableListOf<String>()

    @After(AuditAction::class, inherits = true)
    fun audit(joinPoint: JoinPoint) {
        val action =
            joinPoint.findAnnotation<AuditAction>()?.getArgOrNull<String>("action")
                ?: joinPoint.signature.methodName
        val message = "[AUDIT] action=$action | method=${joinPoint.signature.methodName}"
        auditLogs.add(message)
        auditLogger(message)
    }

    fun clearLogs() = auditLogs.clear()
}
