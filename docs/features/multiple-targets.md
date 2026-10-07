# Multiple Targets

Advice and target annotations are many-to-many: one advice can name several annotations, and
one annotation can be named by several advices.

## One Advice, Multiple Targets

One `@Before` method can target several annotation classes:

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

The advice runs for any function annotated with `@Logged`, `@Traced` or both.

## Multiple Advice, One Target

Several `@Before` methods, in the same aspect or in different ones, can target the same annotation:

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

A function can carry several target annotations, and every matching advice applies:

```kotlin
@Logged @Traced @Metered
fun processCheckout(cart: Cart, userId: String) {
    // All three aspects run their advice before this body
}
```

AspectK applies the matching advice in the order the compiler discovers it.

Ordering is only supported for `@Before`. Combining several `@After` or `@Around` advices on one
function is not supported yet and may run in an unexpected order.

## Identifying the Trigger Annotation

Inside advice, `JoinPoint.signature.annotations` lists the annotations on the intercepted
function:

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
