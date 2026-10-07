# Aspects

An aspect is an `object` annotated with `@Aspect` that groups related advice methods.

!!! warning
    Declare aspects as `object`, not `class`. A `class` aspect is a compilation error.

## Declaring an Aspect

```kotlin
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint

@Aspect
object SecurityAspect {
    @Before(target = [RequiresAuth::class])
    fun checkAuthentication(joinPoint: JoinPoint) {
        val user = getCurrentUser()
            ?: throw UnauthorizedException("Not authenticated")
        println("Access granted to ${joinPoint.signature.methodName} for $user")
    }
}
```

## Multiple Aspects

Several aspects can provide advice for the same target annotation. AspectK applies all of
them, in the order the compiler discovers the aspects.

```kotlin
@Aspect
object LoggingAspect {
    @Before(target = [Audited::class])
    fun log(jp: JoinPoint) { /* ... */ }
}

@Aspect
object AuditAspect {
    @Before(target = [Audited::class])
    fun audit(jp: JoinPoint) { /* ... */ }
}

@Audited
fun deleteUser(userId: String) { /* both log and audit run first */ }
```

!!! warning "Advice Order"
    The order across aspects follows the compiler's IR traversal and can change between
    compiler versions. Write aspects that don't depend on it.

## Aspect Discovery

The compiler plugin scans the module being compiled for `@Aspect` objects, and also picks up
aspects from the other modules of the same Gradle build that it depends on (see
[Cross-Module Injection](../features/cross-module-weaving.md)). Aspects in pre-compiled external
libraries are not discovered.
