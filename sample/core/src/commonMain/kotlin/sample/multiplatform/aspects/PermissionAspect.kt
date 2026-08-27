package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.findAnnotation
import io.github.molelabs.aspectk.runtime.getArgOrNull
import sample.multiplatform.annotations.RequirePermission
import sample.multiplatform.exceptions.PermissionDeniedException

/**
 * Checks the caller's permissions before a function annotated with [RequirePermission] runs.
 *
 * If the permission is missing, throws [PermissionDeniedException] to block execution.
 *
 * ### Example
 * ```kotlin
 * PermissionAspect.grantedPermissions += "ADMIN"
 *
 * @RequirePermission("ADMIN")
 * fun deleteUser(userId: String) { ... }
 * ```
 */
@Aspect
object PermissionAspect {
    /**
     * Currently granted permissions.
     * In a real app this would be populated dynamically from a session or auth service.
     */
    val grantedPermissions = mutableSetOf<String>()

    @Before(RequirePermission::class)
    fun checkPermission(joinPoint: JoinPoint) {
        val permission =
            joinPoint.findAnnotation<RequirePermission>()?.getArgOrNull<String>("permission") ?: return

        if (permission !in grantedPermissions) {
            throw PermissionDeniedException(
                "Permission '$permission' is required to call '${joinPoint.signature.methodName}' " +
                    "but was not granted. Granted: $grantedPermissions",
            )
        }
    }

    /** Clears every granted permission. Use this to isolate test cases. */
    fun revokeAll() = grantedPermissions.clear()
}
