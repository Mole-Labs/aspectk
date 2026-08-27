package sample.multiplatform.exceptions

/** Thrown when a [sample.multiplatform.annotations.RequirePermission] check fails. */
class PermissionDeniedException(message: String) : Exception(message)

/** Thrown on a duplicate call within a [sample.multiplatform.annotations.PreventDoubleClick] cooldown. */
class DoubleClickException(message: String) : Exception(message)
