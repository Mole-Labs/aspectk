package sample.multiplatform.annotations

/**
 * Checks whether the specified permission has been granted before the function runs.
 * If not, throws [sample.multiplatform.exceptions.PermissionDeniedException] to block execution.
 *
 * @param permission Name of the required permission (e.g. "ADMIN", "READ_USER_DATA").
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class RequirePermission(
    val permission: String,
)
