# Multiple Targets

AspectK supports **many-to-many** relationships between advice and target annotations.

## One Advice, Multiple Targets

A single `@Before` method can target multiple annotation classes:

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Logged

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Traced

@Aspect
object ObservabilityAspect {
    @Before(target = [Logged::class, Traced::class])
    fun observe(joinPoint: JoinPoint) {
        println("Intercepting: ${joinPoint.signature.methodName}")
    }
}
```

Functions annotated with either `@Logged` or `@Traced` (or both) will trigger this advice.

## Multiple Advice, One Target

Multiple `@Before` methods (from the same or different aspects) can all target the same annotation:

```kotlin
@Aspect
object LoggingAspect {
    @Before(target = [Audited::class])
    fun log(joinPoint: JoinPoint) {
        println("LOG: ${joinPoint.signature.methodName}")
    }
}

@Aspect
object AuditAspect {
    @Before(target = [Audited::class])
    fun audit(joinPoint: JoinPoint) {
        auditService.record(joinPoint.signature.methodName, joinPoint.args)
    }
}

@Audited
fun deleteAccount(userId: String) {
    // Both log() and audit() run before this body
}
```

## Combining Multiple Annotations on a Function

A function can carry multiple target annotations, triggering all matching advice:

```kotlin
@Logged @Traced @Metered
fun processCheckout(cart: Cart, userId: String) {
    // All three aspects run their advice before this body
}
```

This is a natural composition — AspectK applies all matching advice in compiler-discovery order.

## Identifying the Trigger Annotation

Inside advice, use `JoinPoint.signature.annotations` to determine which annotations are
present on the intercepted function:

```kotlin
@Aspect
object DispatchAspect {
    @Before(target = [Logged::class, Traced::class])
    fun dispatch(joinPoint: JoinPoint) {
        val annotationNames = joinPoint.signature.annotations.map { it.typeName }
        if ("com.example.Logged" in annotationNames) {
            logger.info(joinPoint.signature.methodName)
        }
        if ("com.example.Traced" in annotationNames) {
            tracer.start(joinPoint.signature.methodName)
        }
    }
}
```
