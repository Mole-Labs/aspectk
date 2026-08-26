package sample.multiplatform.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class AuditAction(val action: String = "")
